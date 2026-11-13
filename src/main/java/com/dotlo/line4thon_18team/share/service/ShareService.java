package com.dotlo.line4thon_18team.share.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.share.dto.ConfirmResponse;
import com.dotlo.line4thon_18team.share.dto.ShareResponse;
import com.dotlo.line4thon_18team.share.dto.SharedItineraryResponse;
import com.dotlo.line4thon_18team.share.dto.SharedItineraryResponse.SharedPlace;
import com.dotlo.line4thon_18team.share.entity.TripShare;
import com.dotlo.line4thon_18team.share.repository.TripShareRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.entity.TripStatus;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TripService tripService;
    private final ItineraryService itineraryService;
    private final TripShareRepository tripShareRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final ObjectMapper objectMapper;

    // 확인이 필요한 장소가 있어도 막지 않고 수만 알려준다 (FE가 안내)
    @Transactional
    public ConfirmResponse confirm(Long tripId, Long userId) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        ItineraryVersionView latest = itineraryService.findLatest(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_REQUIRED));
        trip.confirm();

        int placesNeedingCheck = objectMapper.readTree(latest.validation())
                .path("summary").path("placesNeedingCheck").asInt();
        return new ConfirmResponse(tripId, trip.getStatus(), latest.version(), placesNeedingCheck);
    }

    // 여행마다 링크 1개. 다시 요청하면 같은 토큰을 돌려준다
    @Transactional
    public ShareResponse share(Long tripId, Long userId) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        if (trip.getStatus() != TripStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.TRIP_NOT_CONFIRMED);
        }
        // 동시에 두 번 요청하면 둘 다 없다고 보고 저장하다 trip_id UNIQUE 에 걸린다. 그때는 ShareController 가
        // 이 트랜잭션이 끝난 뒤 findShare 로 먼저 생긴 토큰을 돌려준다
        TripShare share = tripShareRepository.findByTripId(tripId)
                .orElseGet(() -> tripShareRepository.saveAndFlush(TripShare.create(trip, newToken())));
        return new ShareResponse(share.getToken());
    }

    public ShareResponse findShare(Long tripId, Long userId) {
        tripService.findMemberTrip(userId, tripId);
        return tripShareRepository.findByTripId(tripId)
                .map(share -> new ShareResponse(share.getToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
    }

    // 로그인 없이 보는 화면. 최신 일정을 보여준다
    public SharedItineraryResponse getShared(String token) {
        Trip trip = tripShareRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND))
                .getTrip();
        ItineraryVersionView itinerary = itineraryService.findLatest(trip.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));

        Set<String> placeIds = itinerary.draft().days().stream()
                .flatMap(day -> day.visits().stream())
                .map(VisitDraft::placeId)
                .collect(Collectors.toSet());
        List<SharedPlace> places = savedPlaceRepository.findAllByTripIdOrderByIdAsc(trip.getId()).stream()
                .filter(place -> placeIds.contains(place.getPlaceId()))
                .map(SharedPlace::from)
                .toList();
        return SharedItineraryResponse.of(trip, itinerary, places);
    }

    // 16바이트 랜덤 → URL에 쓸 수 있는 22글자
    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
