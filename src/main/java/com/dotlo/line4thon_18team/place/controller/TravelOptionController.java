package com.dotlo.line4thon_18team.place.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.city.CityController;
import com.dotlo.line4thon_18team.city.CityController.City;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.place.PlaceMatcher.LatLng;
import com.dotlo.line4thon_18team.place.TravelTime;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TravelOptionController {

    // 도시 밖 제외 기준과 같은 50km
    private static final int MAX_FROM_CITY_M = 50_000;

    private final TripService tripService;
    private final TravelTime travelTime;

    // 구간 "이동 수단 선택" 시트: 도보·대중교통·자동차 순서로 시간과 거리. 구글 경로 조회 최대 3번
    // departAt 은 출발 현지 시각 (대중교통 시간표용). 검증 응답에서 이 방문의 arrival - travel.minutes
    // (앞 방문 종료 + 일정 강도별 여유 시간, 검증이 조회하는 시각과 같음). 없으면 지금
    @GetMapping("/api/trips/{tripId}/travel-options")
    public List<TravelTime.Leg> options(@LoginUser Long userId, @PathVariable Long tripId,
                                        @RequestParam double fromLat, @RequestParam double fromLng,
                                        @RequestParam double toLat, @RequestParam double toLng,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departAt) {
        Trip trip = tripService.findMemberTrip(userId, tripId);   // 없는 여행·구성원이 아니면 404
        City city = CityController.findByName(trip.getCity())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_CITY));
        requireNearCity(city, fromLat, fromLng);
        requireNearCity(city, toLat, toLng);
        Instant departure = departAt == null ? null : departAt.atZone(ZoneId.of(city.timezone())).toInstant();
        return travelTime.options(new LatLng(fromLat, fromLng), new LatLng(toLat, toLng), departure);
    }

    // 여행 도시 밖(잘못된 좌표, NaN 포함)은 구글을 부르지 않음. NaN 은 비교가 늘 false 라 <= 로 확인
    // 위도·경도 범위도 먼저 봄 (경도에 360을 더해도 거리 계산은 같아서 반경 검사를 통과함)
    private static void requireNearCity(City city, double lat, double lng) {
        if (!(Math.abs(lat) <= 90 && Math.abs(lng) <= 180
                && PlaceMatcher.meters(city.lat(), city.lng(), lat, lng) <= MAX_FROM_CITY_M)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "여행 도시 근처 좌표가 아닙니다.");
        }
    }
}
