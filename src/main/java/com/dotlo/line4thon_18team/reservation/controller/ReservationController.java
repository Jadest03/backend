package com.dotlo.line4thon_18team.reservation.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.reservation.dto.FlightsRequest;
import com.dotlo.line4thon_18team.reservation.dto.RentalRequest;
import com.dotlo.line4thon_18team.reservation.dto.ReservationResponse;
import com.dotlo.line4thon_18team.reservation.dto.StaysRequest;
import com.dotlo.line4thon_18team.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    // 이 여행의 예약 정보 (항공, 숙소, 렌터카)
    @GetMapping
    public ResponseEntity<ReservationResponse> get(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(reservationService.get(userId, tripId));
    }

    // 항공권 저장 (항공권 등록 시트). 가는 편·오는 편은 항공편 찾기 결과를 그대로 보낸다. 저장한 뒤 예약 정보 전체를 돌려준다
    @PutMapping("/flights")
    public ResponseEntity<ReservationResponse> putFlights(@LoginUser Long userId, @PathVariable Long tripId,
                                                          @Valid @RequestBody FlightsRequest request) {
        return ResponseEntity.ok(reservationService.putFlights(userId, tripId, request.outbound(), request.returnFlight()));
    }

    // 숙소 전체 저장 (숙소 등록 시트의 "완료"). 저장한 뒤 예약 정보 전체를 돌려준다
    @PutMapping("/stays")
    public ResponseEntity<ReservationResponse> putStays(@LoginUser Long userId, @PathVariable Long tripId,
                                                        @Valid @RequestBody StaysRequest request) {
        return ResponseEntity.ok(reservationService.putStays(userId, tripId, request.stays()));
    }

    // 렌터카 저장 (렌터카 등록 시트). dates가 빈 목록이면 지운다. 저장한 뒤 예약 정보 전체를 돌려준다
    @PutMapping("/rental")
    public ResponseEntity<ReservationResponse> putRental(@LoginUser Long userId, @PathVariable Long tripId,
                                                         @Valid @RequestBody RentalRequest request) {
        return ResponseEntity.ok(reservationService.putRental(userId, tripId, request.dates(), request.placeId()));
    }
}
