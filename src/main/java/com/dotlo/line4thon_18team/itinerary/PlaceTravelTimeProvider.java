package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.itinerary.validation.TravelEstimator;
import com.dotlo.line4thon_18team.itinerary.validation.ValidationPolicy;
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
 *   <li>DRIVE 를 요청하면 자동차 경로를 그대로 쓴다 (렌터카를 빌린 날).</li>
 *   <li>그 밖에는 BE2가 도보·대중교통 중에서 고른다. 검증기가 요청한 수단은 참고만 한다.</li>
 *   <li>BE2의 estimated=true(대중교통 경로 없음, API 오류)는 '추정'으로 넘긴다.</li>
 *   <li>BE2는 거리를 주지 않아서 직선거리 × 우회 계수로 채운다.</li>
 * </ul>
 */
public class PlaceTravelTimeProvider implements TravelTimeProvider {

    private final TravelTime travelTime;
    private final TravelEstimator estimator;

    public PlaceTravelTimeProvider(TravelTime travelTime, ValidationPolicy policy) {
        this.travelTime = travelTime;
        this.estimator = new TravelEstimator(policy);
    }

    @Override
    public TravelLookup lookup(TravelQuery query) {
        TravelTime.Leg leg = query.mode() == TravelMode.DRIVE
                ? travelTime.drive(toLatLng(query.from()), toLatLng(query.to()))
                : travelTime.get(toLatLng(query.from()), toLatLng(query.to()), query.departAt().toInstant());
        int meters = estimator.estimateDistanceMeters(TravelEstimator.straightLineMeters(query.from(), query.to()));
        return leg.estimated()
                ? TravelLookup.estimated(leg.minutes(), meters, toMode(leg.mode()))
                : TravelLookup.ok(leg.minutes(), meters, toMode(leg.mode()));
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
