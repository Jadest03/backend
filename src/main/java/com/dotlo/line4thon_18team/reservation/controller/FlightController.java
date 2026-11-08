package com.dotlo.line4thon_18team.reservation.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.reservation.dto.FlightInfo;
import com.dotlo.line4thon_18team.reservation.service.ReservationService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FlightController {

    private final ReservationService reservationService;

    // 항공편 찾기 (항공권 등록 시트의 "항공편 찾기"). date는 출발 날짜. 못 찾으면 빈 목록
    // 로그인한 사용자만 (AeroDataBox 무료 한도가 월 약 200번이라)
    @GetMapping("/api/flights")
    public List<FlightInfo> find(@LoginUser Long userId, @RequestParam String number, @RequestParam LocalDate date) {
        return reservationService.findFlights(number, date);
    }
}
