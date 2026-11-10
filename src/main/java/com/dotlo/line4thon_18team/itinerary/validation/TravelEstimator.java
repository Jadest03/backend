package com.dotlo.line4thon_18team.itinerary.validation;

import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;

import java.util.Objects;

/** 경로 API를 쓸 수 없을 때 직선거리로 이동시간을 추정한다 (결과는 항상 '추정'으로 표시). */
public final class TravelEstimator {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final ValidationPolicy policy;

    public TravelEstimator(ValidationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** 두 좌표 사이 직선거리(m, 하버사인 공식). */
    public static double straightLineMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** 직선거리 × 우회 계수 ÷ 속도 (+ 대중교통 대기). 최소 1분. */
    public int estimateMinutes(double straightMeters, TravelMode mode) {
        double routeMeters = straightMeters * policy.detourFactor();
        // 운전은 대중교통과 비슷한 속도로 보되 환승·대기 시간은 빼고 센다
        double minutes = switch (mode) {
            case WALK -> routeMeters / policy.walkMetersPerMinute();
            case DRIVE -> routeMeters / policy.transitMetersPerMinute();
            case TRANSIT -> policy.transitOverheadMinutes() + routeMeters / policy.transitMetersPerMinute();
        };
        return (int) Math.max(1, Math.ceil(minutes));
    }

    public int estimateDistanceMeters(double straightMeters) {
        return (int) Math.round(straightMeters * policy.detourFactor());
    }
}
