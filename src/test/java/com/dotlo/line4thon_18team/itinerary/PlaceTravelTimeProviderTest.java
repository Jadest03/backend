package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.itinerary.validation.TravelEstimator;
import com.dotlo.line4thon_18team.itinerary.validation.ValidationPolicy;
import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelLookup;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelQuery;
import com.dotlo.line4thon_18team.place.PlaceMatcher.LatLng;
import com.dotlo.line4thon_18team.place.TravelTime;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceTravelTimeProviderTest {

    private static final GeoPoint NAMBA = new GeoPoint(34.6687, 135.5013);
    private static final GeoPoint OSAKA_CASTLE = new GeoPoint(34.6873, 135.5262);
    private static final ZonedDateTime DEPART = ZonedDateTime.of(2026, 11, 10, 9, 30, 0, 0, ZoneId.of("Asia/Tokyo"));

    @Test
    void BE2가_고른_수단과_시간을_그대로_넘긴다() {
        StubTravelTime be2 = new StubTravelTime(new TravelTime.Leg(12, TravelTime.Mode.WALK, false));
        PlaceTravelTimeProvider provider = new PlaceTravelTimeProvider(be2, ValidationPolicy.defaults());

        TravelLookup lookup = provider.lookup(new TravelQuery(NAMBA, OSAKA_CASTLE, TravelMode.TRANSIT, DEPART));

        assertThat(lookup.status()).isEqualTo(TravelLookup.Status.OK);
        assertThat(lookup.mode()).isEqualTo(TravelMode.WALK);   // 대중교통으로 물었지만 BE2가 도보로 답함
        assertThat(lookup.durationMinutes()).isEqualTo(12);
        assertThat(lookup.estimated()).isFalse();
        assertThat(be2.from).isEqualTo(new LatLng(NAMBA.lat(), NAMBA.lng()));
        assertThat(be2.to).isEqualTo(new LatLng(OSAKA_CASTLE.lat(), OSAKA_CASTLE.lng()));
        assertThat(be2.departure).isEqualTo(DEPART.toInstant());
    }

    @Test
    void BE2_추정치는_추정으로_넘긴다() {
        StubTravelTime be2 = new StubTravelTime(new TravelTime.Leg(35, TravelTime.Mode.TRANSIT, true));
        PlaceTravelTimeProvider provider = new PlaceTravelTimeProvider(be2, ValidationPolicy.defaults());

        TravelLookup lookup = provider.lookup(new TravelQuery(NAMBA, OSAKA_CASTLE, TravelMode.TRANSIT, DEPART));

        assertThat(lookup.status()).isEqualTo(TravelLookup.Status.OK);
        assertThat(lookup.mode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(lookup.durationMinutes()).isEqualTo(35);
        assertThat(lookup.estimated()).isTrue();
    }

    @Test
    void 거리는_직선거리에_우회_계수를_곱해서_채운다() {
        StubTravelTime be2 = new StubTravelTime(new TravelTime.Leg(20, TravelTime.Mode.TRANSIT, false));
        PlaceTravelTimeProvider provider = new PlaceTravelTimeProvider(be2, ValidationPolicy.defaults());
        int expected = new TravelEstimator(ValidationPolicy.defaults())
                .estimateDistanceMeters(TravelEstimator.straightLineMeters(NAMBA, OSAKA_CASTLE));

        TravelLookup lookup = provider.lookup(new TravelQuery(NAMBA, OSAKA_CASTLE, TravelMode.TRANSIT, DEPART));

        assertThat(lookup.distanceMeters()).isEqualTo(expected).isGreaterThan(0);
    }

    @Test
    void 운전을_물으면_자동차_경로를_쓴다() {
        StubTravelTime be2 = new StubTravelTime(new TravelTime.Leg(18, TravelTime.Mode.DRIVE, false));
        PlaceTravelTimeProvider provider = new PlaceTravelTimeProvider(be2, ValidationPolicy.defaults());

        TravelLookup lookup = provider.lookup(new TravelQuery(NAMBA, OSAKA_CASTLE, TravelMode.DRIVE, DEPART));

        assertThat(lookup.mode()).isEqualTo(TravelMode.DRIVE);
        assertThat(lookup.durationMinutes()).isEqualTo(18);
        assertThat(be2.driveCalls).isEqualTo(1);
        assertThat(be2.departure).isNull();   // 자동차는 출발 시각을 받지 않는다
    }

    @Test
    void 운전이_아니면_자동차_경로를_부르지_않는다() {
        StubTravelTime be2 = new StubTravelTime(new TravelTime.Leg(12, TravelTime.Mode.TRANSIT, false));
        PlaceTravelTimeProvider provider = new PlaceTravelTimeProvider(be2, ValidationPolicy.defaults());

        provider.lookup(new TravelQuery(NAMBA, OSAKA_CASTLE, TravelMode.TRANSIT, DEPART));

        assertThat(be2.driveCalls).isZero();
        assertThat(be2.departure).isEqualTo(DEPART.toInstant());
    }

    // BE2 TravelTime 대신 정해진 값을 돌려주고 받은 값을 기록한다 (Google API를 부르지 않음)
    static class StubTravelTime extends TravelTime {

        private final TravelTime.Leg result;
        LatLng from;
        LatLng to;
        Instant departure;
        int driveCalls;

        StubTravelTime(TravelTime.Leg result) {
            super("");
            this.result = result;
        }

        @Override
        public TravelTime.Leg get(LatLng from, LatLng to, Instant departure) {
            this.from = from;
            this.to = to;
            this.departure = departure;
            return result;
        }

        @Override
        public TravelTime.Leg drive(LatLng from, LatLng to) {
            this.from = from;
            this.to = to;
            this.driveCalls++;
            return result;
        }
    }
}
