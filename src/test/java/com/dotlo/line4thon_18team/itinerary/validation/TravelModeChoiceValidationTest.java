package com.dotlo.line4thon_18team.itinerary.validation;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.PlaceInfo;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelQuery;
import com.dotlo.line4thon_18team.itinerary.validation.result.LegSource;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakeTravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY2;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY3;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DOTONBORI;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.UMEDA_SKY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자가 구간 이동 수단을 고른 방문. 그 장소까지 오는 구간만 고른 수단으로 묻고, 나머지는 자동이다.
 */
class TravelModeChoiceValidationTest {

    private final FakeTravelTimeProvider travel = FakeTravelTimeProvider.byDistance();
    private final ItineraryValidator validator =
            new ItineraryValidator(OsakaFixtures.places(), travel, ValidationPolicy.defaults());

    @Test
    void 먼_곳도_도보를_고르면_도보로_묻는다() {
        // 숙소(난바) → 우메다 스카이 약 4.5km. 자동이면 대중교통
        ValidationResult result = validate(DAY3, UMEDA_SKY, TravelMode.WALK);

        TravelQuery toVisit = travel.queries().get(0);
        assertThat(toVisit.mode()).isEqualTo(TravelMode.WALK);
        assertThat(toVisit.chosen()).isTrue();
        assertThat(result.days().get(0).visits().get(0).travel().mode()).isEqualTo(TravelMode.WALK);
        // 숙소로 돌아가는 구간은 고른 적이 없어서 자동
        assertThat(travel.queries().get(1).chosen()).isFalse();
        assertThat(travel.queries().get(1).mode()).isEqualTo(TravelMode.TRANSIT);
    }

    @Test
    void 가까운_곳도_대중교통을_고르면_대중교통으로_묻는다() {
        // 숙소(난바) → 도톤보리 약 340m. 자동이면 도보
        validate(DAY3, DOTONBORI, TravelMode.TRANSIT);

        assertThat(travel.queries().get(0).mode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(travel.queries().get(0).chosen()).isTrue();
    }

    @Test
    void 렌터카_날이어도_고른_수단이_먼저다() {
        validate(DAY2, UMEDA_SKY, TravelMode.TRANSIT);

        assertThat(travel.queries().get(0).mode()).isEqualTo(TravelMode.TRANSIT);
    }

    @Test
    void 고르지_않으면_지금처럼_자동이다() {
        validate(DAY3, UMEDA_SKY, null);

        assertThat(travel.queries()).isNotEmpty().allSatisfy(query -> {
            assertThat(query.mode()).isEqualTo(TravelMode.TRANSIT);
            assertThat(query.chosen()).isFalse();
        });
    }

    @Test
    void 같은_자리여도_고른_수단으로_묻는다() {
        // 도톤보리 바로 옆 가게(0m)로 자동차를 고르면 같은 장소로 건너뛰지 않고 자동차로 조회
        PlaceInfo nextDoor = new PlaceInfo("p-dotonbori-2", "도톤보리 옆 가게", DOTONBORI.location(),
                DOTONBORI.category(), DOTONBORI.openingHours());
        ItineraryValidator sameSpotValidator =
                new ItineraryValidator(OsakaFixtures.places().add(nextDoor), travel, ValidationPolicy.defaults());
        ValidationResult result = sameSpotValidator.validate(ItineraryDraft.of(DayDraft.of(DAY3,
                        new VisitDraft("v-1", DOTONBORI.placeId(), null, null, null),
                        new VisitDraft("v-2", nextDoor.placeId(), null, null, TravelMode.DRIVE))),
                OsakaFixtures.tripWithRentalOnDay2(), PreferenceProfile.defaults(), ValidationOrigin.USER_EDITED);

        TravelQuery toNextDoor = travel.queries().get(1);
        assertThat(toNextDoor.mode()).isEqualTo(TravelMode.DRIVE);
        assertThat(toNextDoor.chosen()).isTrue();
        assertThat(result.days().get(0).visits().get(1).travel().source()).isNotEqualTo(LegSource.SAME_SPOT);
    }

    private ValidationResult validate(LocalDate date, PlaceInfo place, TravelMode mode) {
        ItineraryDraft draft = ItineraryDraft.of(DayDraft.of(date,
                new VisitDraft("v-1", place.placeId(), null, null, mode)));
        return validator.validate(draft, OsakaFixtures.tripWithRentalOnDay2(), PreferenceProfile.defaults(),
                ValidationOrigin.USER_EDITED);
    }
}
