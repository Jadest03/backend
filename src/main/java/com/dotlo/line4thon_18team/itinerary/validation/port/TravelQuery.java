package com.dotlo.line4thon_18team.itinerary.validation.port;

import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 이동시간 조회 요청. 출발 시각은 대중교통 시간표 반영용이다.
 *
 * @param chosen 사용자가 고른 수단인지. true면 이동시간 함수가 다른 수단으로 바꾸지 않는다
 */
public record TravelQuery(GeoPoint from, GeoPoint to, TravelMode mode, ZonedDateTime departAt, boolean chosen) {

    public TravelQuery {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(departAt, "departAt");
    }

    /** 검증기가 거리로 수단을 정한 조회. */
    public TravelQuery(GeoPoint from, GeoPoint to, TravelMode mode, ZonedDateTime departAt) {
        this(from, to, mode, departAt, false);
    }
}
