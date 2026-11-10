package com.dotlo.line4thon_18team.itinerary.version.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItinerarySaveRequest;
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

    // 최신 일정. 아직 일정이 없으면 404
    @GetMapping("/latest")
    public ResponseEntity<ItineraryVersionView> getLatest(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(itineraryService.getLatest(tripId, userId));
    }
}
