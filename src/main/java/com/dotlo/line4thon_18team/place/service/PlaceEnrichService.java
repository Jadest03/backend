package com.dotlo.line4thon_18team.place.service;

import com.dotlo.line4thon_18team.city.CityController;
import com.dotlo.line4thon_18team.city.CityController.City;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.place.PlaceMatcher.Candidate;
import com.dotlo.line4thon_18team.place.PlaceMatcher.MatchResult;
import com.dotlo.line4thon_18team.place.PlaceMatcher.PlaceInput;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장소 보강 (BE2). 매칭 전(PENDING) 장소를 구글 장소와 매칭해서 placeId·주소·좌표·유형을 채운다.
 * 폐업이면 CLOSED, 여행 도시 중심 50km 밖이면 OUT_OF_CITY 로 표시해서 일정에서 뺀다.
 */
@Service
@RequiredArgsConstructor
public class PlaceEnrichService {

    private static final int CITY_LIMIT_M = 50_000;
    private static final String MANUAL_LIST = "직접 추가";   // 직접 추가한 장소의 리스트 이름

    private final TripService tripService;
    private final PlaceMatcher placeMatcher;
    private final SavedPlaceRepository savedPlaceRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    // ponytail: 장소마다 구글 검색 1번씩 순서대로 호출. 장소가 많아 느리면 병렬 호출로 바꾸기
    // 트랜잭션은 장소 1개 저장할 때만 연다. 구글을 기다리는 동안 DB 연결을 잡지 않고,
    // 중간에 구글 오류가 나도 앞 장소 결과는 남는다 (남은 장소는 PENDING → 다시 호출하면 이어서 처리)
    public void enrich(Long userId, Long tripId) {
        City city = cityOf(userId, tripId);
        for (SavedPlace p : savedPlaceRepository.findAllByTripIdAndMatchStatus(tripId, PlaceMatchStatus.PENDING)) {
            PlaceInput input = new PlaceInput(p.getId(), p.getName(), p.getFeatureId(), p.getLatitude(), p.getLongitude());
            MatchResult r = placeMatcher.matchPlaces(List.of(input), city.lat(), city.lng()).get(0);
            transactionTemplate.executeWithoutResult(status -> save(tripId, city, r, PlaceMatchStatus.PENDING));
        }
    }

    // 후보 선택: 확인 필요(NEEDS_REVIEW) 장소를 사용자가 고른 구글 장소로 확정한다. 폐업·도시 밖 판단은 보강과 같다
    public void match(Long userId, Long tripId, Long savedPlaceId, String googlePlaceId) {
        City city = cityOf(userId, tripId);
        SavedPlace place = savedPlaceRepository.findById(savedPlaceId)
                .filter(p -> p.getTrip().getId().equals(tripId))
                .orElseThrow(() -> new BusinessException(ErrorCode.SAVED_PLACE_NOT_FOUND));
        if (place.getMatchStatus() != PlaceMatchStatus.NEEDS_REVIEW) throw new BusinessException(ErrorCode.PLACE_NOT_REVIEWABLE);

        MatchResult r = placeMatcher.details(savedPlaceId, googlePlaceId);   // 없는 placeId면 400
        transactionTemplate.executeWithoutResult(status -> save(tripId, city, r, PlaceMatchStatus.NEEDS_REVIEW));
    }

    // 장소 직접 추가: 검색해서 고른 구글 장소를 "직접 추가" 리스트로 저장한다. 이미 있는 장소면 아무것도 하지 않는다
    public void add(Long userId, Long tripId, String googlePlaceId) {
        City city = cityOf(userId, tripId);
        if (savedPlaceRepository.findFirstByTripIdAndPlaceId(tripId, googlePlaceId).isPresent()) return;   // 구글 호출 아끼기

        MatchResult r = placeMatcher.details(null, googlePlaceId);   // 없는 placeId면 400
        Candidate c = r.place();
        transactionTemplate.executeWithoutResult(status -> {
            // 여행 행을 잠가서 같은 여행의 추가를 한 번에 하나씩 처리한다.
            // 안 잠그면 추가 버튼을 연달아 눌렀을 때 둘 다 "없음"으로 보고 같은 장소가 2~3행 생김 (2026-11-06 확인)
            Trip trip = entityManager.find(Trip.class, tripId, LockModeType.PESSIMISTIC_WRITE);
            // 그사이 같은 장소가 들어왔으면 (같은 요청 두 번, 업로드 후 보강) 추가하지 않는다
            if (savedPlaceRepository.findFirstByTripIdAndPlaceId(tripId, c.placeId()).isPresent()) return;
            SavedPlace place = SavedPlace.fromSearch(trip, c.name(), MANUAL_LIST);
            place.applyMatch(c.name(), c.placeId(), c.address(), c.lat(), c.lng(), r.category(), statusOf(city, r));
            savedPlaceRepository.save(place);
        });
    }

    private City cityOf(Long userId, Long tripId) {
        Trip trip = tripService.findOwnedTrip(userId, tripId);   // 없는 여행·남의 여행이면 404
        return CityController.findByName(trip.getCity())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_CITY));
    }

    // from: 저장 직전에도 이 상태여야 저장한다
    private void save(Long tripId, City city, MatchResult r, PlaceMatchStatus from) {
        // 직접 추가와 같은 여행 잠금. 안 잠그면 보강과 직접 추가가 같은 장소를 동시에 저장할 때 2행이 됨 (2026-11-08 20번 중 8번)
        entityManager.find(Trip.class, tripId, LockModeType.PESSIMISTIC_WRITE);
        SavedPlace place = savedPlaceRepository.findById(r.id()).orElse(null);
        // 그사이 사용자가 삭제했거나, 동시에 들어온 다른 요청이 먼저 처리한 장소.
        // 처리된 장소는 placeId 가 있어서, 그냥 진행하면 아래 중복 확인에서 자기 자신을 찾아 지운다
        if (place == null || place.getMatchStatus() != from) return;
        if (r.status() != PlaceMatcher.MatchStatus.MATCHED) {
            place.updateMatchStatus(PlaceMatchStatus.valueOf(r.status().name()));   // NEEDS_REVIEW / NOT_FOUND
            return;
        }
        Candidate c = r.place();
        // 같은 가게를 장소로도 핀으로도 저장했으면 이 행의 리스트 이름·메모를 기존 행으로 옮기고 이 행은 지운다
        SavedPlace same = savedPlaceRepository.findFirstByTripIdAndPlaceId(tripId, c.placeId()).orElse(null);
        if (same != null) {
            same.mergeFrom(place);
            savedPlaceRepository.delete(place);
            return;
        }
        // 이름 없는 핀(이름이 좌표)은 구글 장소 이름으로
        String name = PlaceMatcher.isCoordinates(place.getName()) ? c.name() : place.getName();
        place.applyMatch(name, c.placeId(), c.address(), c.lat(), c.lng(), r.category(), statusOf(city, r));
    }

    // 찾은 장소의 상태: 폐업이면 CLOSED, 여행 도시 중심 50km 밖이면 OUT_OF_CITY
    private static PlaceMatchStatus statusOf(City city, MatchResult r) {
        Candidate c = r.place();
        if (r.closed()) return PlaceMatchStatus.CLOSED;
        if (PlaceMatcher.meters(city.lat(), city.lng(), c.lat(), c.lng()) > CITY_LIMIT_M) return PlaceMatchStatus.OUT_OF_CITY;
        return PlaceMatchStatus.MATCHED;
    }
}
