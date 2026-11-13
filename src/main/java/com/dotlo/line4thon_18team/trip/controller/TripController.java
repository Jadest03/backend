package com.dotlo.line4thon_18team.trip.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.trip.dto.TripCreateRequest;
import com.dotlo.line4thon_18team.trip.dto.TripResponse;
import com.dotlo.line4thon_18team.trip.dto.TripUpdateRequest;
import com.dotlo.line4thon_18team.trip.service.TripDeleteService;
import com.dotlo.line4thon_18team.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final TripDeleteService tripDeleteService;

    // 여행 생성 → 201
    @PostMapping
    public ResponseEntity<TripResponse> createTrip(@LoginUser Long userId,
                                                   @Valid @RequestBody TripCreateRequest request) {
        TripResponse response = tripService.createTrip(userId, request);
        return ResponseEntity.created(URI.create("/api/trips/" + response.id())).body(response);
    }

    // 내 여행 목록 (최신순)
    @GetMapping
    public ResponseEntity<List<TripResponse>> getMyTrips(@LoginUser Long userId) {
        return ResponseEntity.ok(tripService.getMyTrips(userId));
    }

    // 작성 중인 여행 → 있으면 200, 없으면 204
    @GetMapping("/in-progress")
    public ResponseEntity<TripResponse> getInProgressTrip(@LoginUser Long userId) {
        return tripService.getInProgressTrip(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // 여행 상세 (남의 여행이면 404)
    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> getTrip(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.getTrip(userId, tripId));
    }

    // 기본정보 수정 (보낸 값만)
    @PatchMapping("/{tripId}")
    public ResponseEntity<TripResponse> updateTrip(@LoginUser Long userId, @PathVariable Long tripId,
                                                   @Valid @RequestBody TripUpdateRequest request) {
        return ResponseEntity.ok(tripService.updateTrip(userId, tripId, request));
    }

    // 여행 삭제 (방장만) → 204
    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> deleteTrip(@LoginUser Long userId, @PathVariable Long tripId) {
        tripDeleteService.deleteTrip(userId, tripId);
        return ResponseEntity.noContent().build();
    }
}
