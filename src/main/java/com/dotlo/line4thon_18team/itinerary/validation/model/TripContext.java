package com.dotlo.line4thon_18team.itinerary.validation.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/**
 * 검증에 필요한 여행 정보 (BE1 여행, BE2 항공·숙소 데이터에서 만든다). 시각은 모두 여행지 현지 시각이다.
 *
 * <p>숙소는 밤마다 다를 수 있다. 오사카에서 이틀 자고 교토에서 하루 자면, 가운데 날은 오사카에서 출발해
 * 교토에서 끝난다. 그래서 하루의 출발지와 도착지를 {@link #startOf}·{@link #endOf}로 따로 묻는다.
 *
 * @param zone         여행지 시간대 (예: Asia/Tokyo). Routes API 출발 시각 계산에 쓴다
 * @param nightlyStays 그 날 "밤" 묵는 곳. 숙소를 하나도 입력하지 않았으면 비어 있다
 * @param base         숙소를 모를 때 쓰는 기준점 (도시 중심). 하루를 여기서 출발해 여기로 돌아오는 것으로 본다
 * @param airport      여행지 공항 좌표 (없으면 공항 이동시간 기본값 사용)
 * @param arrivalAt    첫날 항공 도착 시각 (없으면 첫날도 평소처럼 계산)
 * @param departureAt  마지막 날 항공 출발 시각 (없으면 마지막 날도 평소처럼 계산)
 */
public record TripContext(ZoneId zone,
                          LocalDate startDate,
                          LocalDate endDate,
                          Map<LocalDate, GeoPoint> nightlyStays,
                          GeoPoint base,
                          GeoPoint airport,
                          LocalDateTime arrivalAt,
                          LocalDateTime departureAt) {

    public TripContext {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(base, "base");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다: " + startDate + " ~ " + endDate);
        }
        nightlyStays = nightlyStays == null ? Map.of() : Map.copyOf(nightlyStays);
    }

    /** 숙소를 입력하지 않은 여행. 모든 날을 base에서 출발해 base로 돌아오는 것으로 본다. */
    public static TripContext withoutStays(ZoneId zone, LocalDate startDate, LocalDate endDate, GeoPoint base,
                                           GeoPoint airport, LocalDateTime arrivalAt, LocalDateTime departureAt) {
        return new TripContext(zone, startDate, endDate, Map.of(), base, airport, arrivalAt, departureAt);
    }

    /**
     * 그 날 일정을 시작하는 곳. 전날 밤 묵은 숙소에서 나오기 때문에 전날 밤 숙소를 먼저 본다.
     * 첫날은 전날 밤이 없어서 그날 밤 묵을 숙소에서 출발하는 것으로 본다 (공항에서 체크인하고 나오는 흐름).
     */
    public GeoPoint startOf(LocalDate date) {
        GeoPoint previousNight = nightlyStays.get(date.minusDays(1));
        if (previousNight != null) {
            return previousNight;
        }
        return nightlyStays.getOrDefault(date, base);
    }

    /**
     * 그 날 일정을 끝내고 돌아가는 곳. 그날 밤 묵을 숙소다.
     * 마지막 날은 그날 밤이 없어서 전날 밤 묵던 숙소로 돌아오는 것으로 본다 (짐을 찾아 공항으로 가는 흐름).
     */
    public GeoPoint endOf(LocalDate date) {
        GeoPoint thisNight = nightlyStays.get(date);
        if (thisNight != null) {
            return thisNight;
        }
        return nightlyStays.getOrDefault(date.minusDays(1), base);
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
