package com.dotlo.line4thon_18team.itinerary.version.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItinerarySaveRequest;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionSummary;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/itineraries")
public class ItineraryController {

    private final ItineraryService itineraryService;

    // 사용자가 직접 고친 일정 저장 → 201
    @PostMapping
    public ResponseEntity<ItineraryVersionView> saveManualEdit(@LoginUser Long userId, @PathVariable Long tripId,
                                                               @Valid @RequestBody ItinerarySaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itineraryService.saveManualEdit(tripId, userId, request));
    }

    // 그 버전의 일정으로 되돌린다. 버전을 지우지 않고 새 버전(REVERT)으로 쌓는다. 요청 바디는 없다
    @PostMapping("/{version}/revert")
    public ResponseEntity<ItineraryVersionView> revert(@LoginUser Long userId, @PathVariable Long tripId,
                                                       @PathVariable int version) {
        return ResponseEntity.ok(itineraryService.revert(tripId, userId, version));
    }

    // 일정 변경 이력. 최신 버전부터. 일정 내용(draft)과 검증 결과(validation)는 담지 않는다
    @GetMapping
    public ResponseEntity<List<ItineraryVersionSummary>> history(@LoginUser Long userId,
                                                                 @PathVariable Long tripId) {
        return ResponseEntity.ok(itineraryService.findHistory(tripId, userId));
    }

    // 최신 일정. 아직 일정이 없으면 404
    @GetMapping("/latest")
    public ResponseEntity<ItineraryVersionView> getLatest(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(itineraryService.getLatest(tripId, userId));
    }
}
