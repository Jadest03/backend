package com.dotlo.line4thon_18team.place.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.place.dto.GooglePlaceRequest;
import com.dotlo.line4thon_18team.place.dto.PlaceImportResponse;
import com.dotlo.line4thon_18team.place.dto.PlaceLinkRequest;
import com.dotlo.line4thon_18team.place.dto.SavedPlaceListResponse;
import com.dotlo.line4thon_18team.place.service.PlaceEnrichService;
import com.dotlo.line4thon_18team.place.service.SavedPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/places")
public class SavedPlaceController {

    private final SavedPlaceService savedPlaceService;
    private final PlaceEnrichService placeEnrichService;

    // 이 여행에 저장된 장소 목록
    @GetMapping
    public ResponseEntity<SavedPlaceListResponse> getPlaces(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(savedPlaceService.getPlaces(userId, tripId));
    }

    // 장소 직접 추가: 검색해서 고른 구글 장소를 "직접 추가" 리스트로 저장하고, 전체 목록을 돌려준다
    // 이미 있는 장소면 새로 만들지 않고 목록만 돌려준다
    @PostMapping
    public ResponseEntity<SavedPlaceListResponse> addPlace(@LoginUser Long userId, @PathVariable Long tripId,
                                                           @Valid @RequestBody GooglePlaceRequest request) {
        placeEnrichService.add(userId, tripId, request.placeId());
        return ResponseEntity.ok(savedPlaceService.getPlaces(userId, tripId));
    }

    // Takeout CSV 하나 또는 Takeout zip 업로드 (form-data, key: file)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaceImportResponse> uploadTakeout(@LoginUser Long userId, @PathVariable Long tripId,
                                                             @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(savedPlaceService.uploadTakeout(userId, tripId, file));
    }

    // 구글맵 공유 리스트 링크로 가져오기 (body: {"url": "https://maps.app.goo.gl/..."})
    @PostMapping("/link")
    public ResponseEntity<PlaceImportResponse> importSharedList(@LoginUser Long userId, @PathVariable Long tripId,
                                                                @Valid @RequestBody PlaceLinkRequest request) {
        return ResponseEntity.ok(savedPlaceService.importSharedList(userId, tripId, request.url()));
    }

    // 장소 보강: 매칭 전(PENDING) 장소에 placeId·주소·좌표·유형을 채우고, 보강 후 전체 목록을 돌려준다
    // 업로드가 끝나면 FE가 바로 호출. 다시 불러도 PENDING만 처리해서 구글 호출이 반복되지 않는다
    @PostMapping("/enrich")
    public ResponseEntity<SavedPlaceListResponse> enrichPlaces(@LoginUser Long userId, @PathVariable Long tripId) {
        placeEnrichService.enrich(userId, tripId);
        return ResponseEntity.ok(savedPlaceService.getPlaces(userId, tripId));
    }

    // 후보 선택: 확인 필요(NEEDS_REVIEW) 장소를 사용자가 고른 구글 장소로 확정하고, 전체 목록을 돌려준다
    // 경로의 placeId 는 저장 장소 id, 본문의 placeId 는 구글 장소 ID. 같은 장소가 이미 있으면 합쳐져서 이 장소는 목록에서 빠진다
    @PatchMapping("/{placeId}/match")
    public ResponseEntity<SavedPlaceListResponse> matchPlace(@LoginUser Long userId, @PathVariable Long tripId,
                                                             @PathVariable Long placeId,
                                                             @Valid @RequestBody GooglePlaceRequest request) {
        placeEnrichService.match(userId, tripId, placeId, request.placeId());
        return ResponseEntity.ok(savedPlaceService.getPlaces(userId, tripId));
    }

    // 저장 장소 삭제 → 204 No Content
    @DeleteMapping("/{placeId}")
    public ResponseEntity<Void> deletePlace(@LoginUser Long userId, @PathVariable Long tripId,
                                            @PathVariable Long placeId) {
        savedPlaceService.deletePlace(userId, tripId, placeId);
        return ResponseEntity.noContent().build();
    }
}
