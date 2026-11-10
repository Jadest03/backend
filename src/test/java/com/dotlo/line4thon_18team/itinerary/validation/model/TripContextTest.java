package com.dotlo.line4thon_18team.itinerary.validation.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 날짜별 숙소에서 하루의 출발지·도착지를 고르는 규칙. */
class TripContextTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final LocalDate DAY1 = LocalDate.of(2026, 12, 14);
    private static final LocalDate DAY2 = DAY1.plusDays(1);
    private static final LocalDate DAY3 = DAY1.plusDays(2);
    private static final LocalDate DAY4 = DAY1.plusDays(3);

    private static final GeoPoint NAMBA = new GeoPoint(34.6656, 135.5013);
    private static final GeoPoint UMEDA = new GeoPoint(34.7055, 135.4983);
    private static final GeoPoint CITY_CENTER = new GeoPoint(34.6937, 135.5023);

    @Test
    void 숙소를_입력하지_않으면_모든_날이_기준점이다() {
        TripContext trip = TripContext.withoutStays(ZONE, DAY1, DAY4, CITY_CENTER, null, null, null);

        for (LocalDate date : new LocalDate[]{DAY1, DAY2, DAY3, DAY4}) {
            assertThat(trip.startOf(date)).isEqualTo(CITY_CENTER);
            assertThat(trip.endOf(date)).isEqualTo(CITY_CENTER);
        }
    }

    @Test
    void 숙소를_옮기는_날은_전날_밤에서_출발해_그날_밤으로_간다() {
        TripContext trip = movingToUmeda();

        assertThat(trip.startOf(DAY3)).isEqualTo(NAMBA);
        assertThat(trip.endOf(DAY3)).isEqualTo(UMEDA);
    }

    @Test
    void 첫날은_그날_밤_묵을_곳에서_출발한_것으로_본다() {
        TripContext trip = movingToUmeda();

        assertThat(trip.startOf(DAY1)).isEqualTo(NAMBA);
        assertThat(trip.endOf(DAY1)).isEqualTo(NAMBA);
    }

    @Test
    void 마지막_날은_전날_밤_묵던_곳으로_돌아온다() {
        TripContext trip = movingToUmeda();

        assertThat(trip.startOf(DAY4)).isEqualTo(UMEDA);
        assertThat(trip.endOf(DAY4)).isEqualTo(UMEDA);
    }

    @Test
    void 가운데_날은_같은_숙소면_출발지와_도착지가_같다() {
        TripContext trip = movingToUmeda();

        assertThat(trip.startOf(DAY2)).isEqualTo(NAMBA);
        assertThat(trip.endOf(DAY2)).isEqualTo(NAMBA);
    }

    @Test
    void 렌터카를_빌린_날만_운전하는_날이다() {
        TripContext trip = new TripContext(ZONE, DAY1, DAY4, Map.of(), CITY_CENTER,
                null, null, Set.of(DAY2), null, null);

        assertThat(trip.drivingOn(DAY2)).isTrue();
        assertThat(trip.drivingOn(DAY1)).isFalse();
        assertThat(trip.drivingOn(DAY3)).isFalse();
    }

    private static TripContext movingToUmeda() {
        return new TripContext(ZONE, DAY1, DAY4,
                Map.of(DAY1, NAMBA, DAY2, NAMBA, DAY3, UMEDA),
                CITY_CENTER, null, null, Set.of(), null, null);
    }
}
