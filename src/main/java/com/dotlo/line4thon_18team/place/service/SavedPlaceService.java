package com.dotlo.line4thon_18team.place.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.dto.PlaceImportResponse;
import com.dotlo.line4thon_18team.place.dto.SavedPlaceListResponse;
import com.dotlo.line4thon_18team.place.dto.SavedPlaceResponse;
import com.dotlo.line4thon_18team.place.entity.PlaceSource;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.importer.ImportedList;
import com.dotlo.line4thon_18team.place.importer.ImportedPlace;
import com.dotlo.line4thon_18team.place.link.GoogleListClient;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.place.takeout.TakeoutParser;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavedPlaceService {

    private final TripService tripService;
    private final SavedPlaceRepository savedPlaceRepository;
    private final TakeoutParser takeoutParser;
    private final GoogleListClient googleListClient;
    private final TransactionTemplate transactionTemplate;

    public SavedPlaceListResponse getPlaces(Long userId, Long tripId) {
        tripService.findMemberTrip(userId, tripId);   // 없는 여행·구성원이 아니면 404

        List<SavedPlaceResponse> places = savedPlaceRepository.findAllByTripIdOrderByIdAsc(tripId).stream()
                .map(SavedPlaceResponse::from)
                .toList();
        return new SavedPlaceListResponse(tripId, places.size(), places);
    }

    // Takeout CSV/zip 업로드
    @Transactional
    public PlaceImportResponse uploadTakeout(Long userId, Long tripId, MultipartFile file) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        List<ImportedList> lists = takeoutParser.parse(file);   // 파일 문제면 여기서 400
        return saveImported(trip, lists, PlaceSource.TAKEOUT);
    }

    // 구글맵 공유 리스트 링크로 가져오기
    // 구글을 기다리는 동안(2초 정도) DB 연결을 잡지 않도록, 저장할 때만 트랜잭션을 연다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PlaceImportResponse importSharedList(Long userId, Long tripId, String url) {
        tripService.findMemberTrip(userId, tripId);   // 구성원이 아니면 구글에 요청하기 전에 404
        ImportedList list = googleListClient.fetch(url);
        if (list.places().isEmpty()) {
            throw new BusinessException(ErrorCode.NO_SAVED_PLACES, "리스트에 저장된 장소가 없습니다.");
        }
        return transactionTemplate.execute(status ->
                saveImported(tripService.findMemberTrip(userId, tripId), List.of(list), PlaceSource.SHARED_LINK));
    }

    /**
     * 읽은 장소 저장 (업로드, 공유 링크 공용).
     * 같은 장소 판별: feature ID 가 같으면 같은 장소, feature ID 가 없는 핀은 URL 이 같으면 같은 장소.
     * 같은 장소면 새로 저장하지 않고 리스트 이름만 추가한다 (메모가 비어 있으면 메모도 채운다).
     */
    private PlaceImportResponse saveImported(Trip trip, List<ImportedList> lists, PlaceSource source) {
        Map<String, SavedPlace> byFeatureId = new HashMap<>();
        Map<String, SavedPlace> byUrl = new HashMap<>();
        for (SavedPlace place : savedPlaceRepository.findAllByTripIdOrderByIdAsc(trip.getId())) {
            if (place.getFeatureId() != null) {
                byFeatureId.put(place.getFeatureId(), place);
            } else if (place.getGoogleUrl() != null) {
                byUrl.put(place.getGoogleUrl(), place);
            }
        }

        List<PlaceImportResponse.ListResult> results = new ArrayList<>();
        int totalNewCount = 0;
        for (ImportedList list : lists) {
            int newCount = 0;
            for (ImportedPlace row : list.places()) {
                Map<String, SavedPlace> index = row.featureId() != null ? byFeatureId : byUrl;
                String key = row.featureId() != null ? row.featureId() : row.googleUrl();

                SavedPlace existing = index.get(key);
                if (existing != null) {
                    existing.addListName(list.listName());
                    existing.fillNoteIfEmpty(row.note());
                    continue;
                }
                SavedPlace created = savedPlaceRepository.save(source == PlaceSource.SHARED_LINK
                        ? SavedPlace.fromSharedLink(trip, row.featureId(), row.name(), row.note(), row.googleUrl(),
                                row.latitude(), row.longitude(), list.listName())
                        : SavedPlace.fromTakeout(trip, row.featureId(), row.name(), row.note(), row.googleUrl(),
                                row.latitude(), row.longitude(), list.listName()));
                index.put(key, created);   // 같은 파일 안 다른 리스트에서 또 나와도 중복으로 처리
                newCount++;
            }
            totalNewCount += newCount;
            results.add(new PlaceImportResponse.ListResult(list.listName(), list.places().size(),
                    newCount, list.places().size() - newCount, list.skippedRows()));
        }
        return new PlaceImportResponse(trip.getId(), totalNewCount, results);
    }

    @Transactional
    public void deletePlace(Long userId, Long tripId, Long placeId) {
        tripService.findMemberTrip(userId, tripId);   // 없는 여행·구성원이 아니면 404 (구성원 모두 편집 가능)

        SavedPlace place = savedPlaceRepository.findById(placeId)
                .filter(p -> p.getTrip().getId().equals(tripId))   // 다른 여행의 장소면 없는 것으로 본다
                .orElseThrow(() -> new BusinessException(ErrorCode.SAVED_PLACE_NOT_FOUND));
        savedPlaceRepository.delete(place);
    }
}
