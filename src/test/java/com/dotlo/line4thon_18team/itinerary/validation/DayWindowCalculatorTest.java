package com.dotlo.line4thon_18team.itinerary.validation;

import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.model.TripContext;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelLookup;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelQuery;
import com.dotlo.line4thon_18team.itinerary.validation.result.DayWindow;
import com.dotlo.line4thon_18team.itinerary.validation.result.WindowAdjustment;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakeTravelTimeProvider;
import com.dotlo.line4thon_18team.preference.domain.DayStartPreference;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.preference.domain.TravelPace;
import com.dotlo.line4thon_18team.preference.domain.WalkingPreference;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY1;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY2;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY4;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.HOTEL_NAMBA;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.KIX;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.ZONE;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;

class DayWindowCalculatorTest {

    private final FakeTravelTimeProvider travel = FakeTravelTimeProvider.fixedMinutes(50);
    private final DayWindowCalculator calculator = new DayWindowCalculator(travel, ValidationPolicy.defaults());

    @Test
    void 항공_정보가_없으면_매일_같은_시간대다() {
        List<DayWindow> windows = calculator.calculate(trip(), PreferenceProfile.defaults());

        assertThat(windows).hasSize(4);
        assertThat(windows).allSatisfy(window -> {
            assertThat(window.start().toLocalTime()).isEqualTo(LocalTime.of(9, 30));
            assertThat(window.end().toLocalTime()).isEqualTo(LocalTime.of(21, 30));
            assertThat(window.adjustments()).isEmpty();
            assertThat(window.availableMinutes()).isEqualTo(720);
        });
        assertThat(travel.queries()).isEmpty();
    }

    @Test
    void 하루_시작_시각은_취향을_따른다() {
        assertThat(calculator.calculate(trip(), profile(DayStartPreference.EARLY)).get(0).start())
                .isEqualTo(DAY1.atTime(8, 30));
        assertThat(calculator.calculate(trip(), profile(DayStartPreference.LATE)).get(0).start())
                .isEqualTo(DAY1.atTime(11, 0));
    }

    @Test
    void 도착일은_입국_공항이동_짐정리_뒤부터_시작한다() {
        TripContext trip = TripContext.withoutStays(ZONE, DAY1, DAY4, HOTEL_NAMBA, KIX, DAY1.atTime(13, 0), null);

        List<DayWindow> windows = calculator.calculate(trip, PreferenceProfile.defaults());

        // 13:00 도착 + 입국 60 + 공항→숙소 50 + 짐 정리 30
        assertThat(windows.get(0).start()).isEqualTo(DAY1.atTime(15, 20));
        assertThat(windows.get(0).adjustments()).containsExactly(WindowAdjustment.SHORTENED_BY_ARRIVAL);
        assertThat(windows.get(1).start()).isEqualTo(DAY2.atTime(9, 30));
        assertThat(windows.get(1).adjustments()).isEmpty();

        TravelQuery query = travel.queries().get(0);
        assertThat(query.from()).isEqualTo(KIX);
        assertThat(query.to()).isEqualTo(HOTEL_NAMBA);
        assertThat(query.mode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(query.departAt()).isEqualTo(DAY1.atTime(14, 0).atZone(ZONE));
    }

    @Test
    void 출발일은_공항에_가기_전에_숙소로_돌아오는_시각까지다() {
        TripContext trip = TripContext.withoutStays(ZONE, DAY1, DAY4, HOTEL_NAMBA, KIX, null, DAY4.atTime(15, 0));

        List<DayWindow> windows = calculator.calculate(trip, PreferenceProfile.defaults());

        // 15:00 출발 − 공항 120 − 숙소→공항 50 − 짐 찾기 30
        assertThat(windows.get(3).end()).isEqualTo(DAY4.atTime(11, 40));
        assertThat(windows.get(3).adjustments()).containsExactly(WindowAdjustment.SHORTENED_BY_DEPARTURE);
        assertThat(windows.get(2).end()).isEqualTo(DAY4.minusDays(1).atTime(21, 30));
    }

    @Test
    void 밤늦게_도착하면_첫날은_일정을_넣을_시간이_없다() {
        TripContext trip = TripContext.withoutStays(ZONE, DAY1, DAY4, HOTEL_NAMBA, KIX, DAY1.atTime(22, 30), null);

        List<DayWindow> windows = calculator.calculate(trip, PreferenceProfile.defaults());

        assertThat(windows.get(0).isEmpty()).isTrue();
        assertThat(windows.get(0).availableMinutes()).isZero();
        assertThat(windows.get(1).start()).isEqualTo(DAY2.atTime(9, 30));
    }

    @Test
    void 공항_좌표가_없으면_기본_이동시간을_쓴다() {
        TripContext trip = TripContext.withoutStays(ZONE, DAY1, DAY4, HOTEL_NAMBA, null, DAY1.atTime(13, 0), null);

        List<DayWindow> windows = calculator.calculate(trip, PreferenceProfile.defaults());

        assertThat(windows.get(0).start()).isEqualTo(DAY1.atTime(15, 30));
        assertThat(travel.queries()).isEmpty();
    }

    @Test
    void 공항_경로_조회가_실패하면_직선거리로_추정한다() {
        FakeTravelTimeProvider failing = FakeTravelTimeProvider.fixedMinutes(50)
                .override(KIX, HOTEL_NAMBA, TravelLookup.error("quota"));
        DayWindowCalculator calc = new DayWindowCalculator(failing, ValidationPolicy.defaults());
        TripContext trip = TripContext.withoutStays(ZONE, DAY1, DAY4, HOTEL_NAMBA, KIX, DAY1.atTime(9, 0), null);
        int estimated = new TravelEstimator(ValidationPolicy.defaults())
                .estimateMinutes(TravelEstimator.straightLineMeters(KIX, HOTEL_NAMBA), TravelMode.TRANSIT);

        List<DayWindow> windows = calc.calculate(trip, PreferenceProfile.defaults());

        assertThat(windows.get(0).start()).isEqualTo(DAY1.atTime(9, 0).plusMinutes(60 + estimated + 30));
    }

    private static PreferenceProfile profile(DayStartPreference dayStart) {
        return new PreferenceProfile(TravelPace.NORMAL, WalkingPreference.NORMAL, dayStart, List.of(), List.of(), List.of());
    }
}
