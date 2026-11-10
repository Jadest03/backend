package com.dotlo.line4thon_18team.itinerary.version.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/itineraries")
public class ItineraryController {

    private final ItineraryService itineraryService;

    // 최신 일정. 아직 일정이 없으면 404
    @GetMapping("/latest")
    public ResponseEntity<ItineraryVersionView> getLatest(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(itineraryService.getLatest(userId, tripId));
    }
}
