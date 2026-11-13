package com.dotlo.line4thon_18team.place.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.city.CityController;
import com.dotlo.line4thon_18team.city.CityController.City;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.place.PlaceMatcher.Candidate;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlaceSearchController {

    // 도시 밖 제외 기준과 같은 50km. Places API 검색 범위 최대값이기도 함
    private static final int SEARCH_RADIUS_M = 50_000;

    private final TripService tripService;
    private final PlaceMatcher placeMatcher;

    // 장소·숙소 검색. 여행 도시 중심 50km 안, 최대 10개. 결과가 없으면 빈 목록 (FE가 주소 직접 입력으로 안내)
    @GetMapping("/api/places/search")
    public List<Candidate> search(@LoginUser Long userId, @RequestParam String query, @RequestParam Long tripId) {
        if (query.isBlank()) throw new BusinessException(ErrorCode.INVALID_INPUT, "query 값이 비어 있습니다.");
        // 구글이 좌표 검색어를 400으로 거절해서 502가 나던 것 → 우리 쪽 400 (이름 없는 핀 이름이 좌표라 FE가 그대로 넣을 수 있음)
        if (PlaceMatcher.isCoordinates(query)) throw new BusinessException(ErrorCode.INVALID_INPUT, "좌표로는 검색할 수 없습니다. 장소 이름을 입력해 주세요.");
        Trip trip = tripService.findMemberTrip(userId, tripId);   // 없는 여행·구성원이 아니면 404
        City city = CityController.findByName(trip.getCity())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_CITY));
        return placeMatcher.searchNear(query, city.lat(), city.lng(), SEARCH_RADIUS_M);
    }
}
