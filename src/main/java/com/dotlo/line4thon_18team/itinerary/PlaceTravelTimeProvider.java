package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelLookup;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelQuery;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.place.PlaceMatcher.LatLng;
import com.dotlo.line4thon_18team.place.TravelTime;

/**
 * BE2 이동시간(place.TravelTime)을 일정 검증의 TravelTimeProvider로 연결한다.
 *
 * <ul>
 *   <li>사용자가 고른 수단(chosen)이면 그 수단 그대로 조회한다.</li>
 *   <li>DRIVE 를 요청하면 자동차 경로를 그대로 쓴다 (렌터카를 빌린 날).</li>
 *   <li>그 밖에는 BE2가 도보·대중교통 중에서 고른다. 검증기가 요청한 수단은 참고만 한다.</li>
 *   <li>BE2의 estimated=true(대중교통 경로 없음, API 오류)는 '추정'으로 넘긴다.</li>
 *   <li>거리는 BE2가 준 경로 거리. 추정치면 BE2도 직선거리 × 1.3 이라 검증기 추정과 같다.</li>
 * </ul>
 */
public class PlaceTravelTimeProvider implements TravelTimeProvider {

    private final TravelTime travelTime;

    public PlaceTravelTimeProvider(TravelTime travelTime) {
        this.travelTime = travelTime;
    }

    @Override
    public TravelLookup lookup(TravelQuery query) {
        LatLng from = toLatLng(query.from());
        LatLng to = toLatLng(query.to());
        TravelTime.Leg leg;
        if (query.mode() == TravelMode.DRIVE) {
            leg = travelTime.drive(from, to);
        } else if (!query.chosen()) {
            leg = travelTime.get(from, to, query.departAt().toInstant());
        } else if (query.mode() == TravelMode.WALK) {
            leg = travelTime.walk(from, to);
        } else {
            leg = travelTime.transit(from, to, query.departAt().toInstant());
        }
        return leg.estimated()
                ? TravelLookup.estimated(leg.minutes(), leg.distanceMeters(), toMode(leg.mode()))
                : TravelLookup.ok(leg.minutes(), leg.distanceMeters(), toMode(leg.mode()));
    }

    private static TravelMode toMode(TravelTime.Mode mode) {
        return switch (mode) {
            case WALK -> TravelMode.WALK;
            case DRIVE -> TravelMode.DRIVE;
            case TRANSIT -> TravelMode.TRANSIT;
        };
    }

    private static LatLng toLatLng(GeoPoint point) {
        return new LatLng(point.lat(), point.lng());
    }
}
