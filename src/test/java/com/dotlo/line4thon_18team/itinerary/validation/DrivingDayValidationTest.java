package com.dotlo.line4thon_18team.itinerary.validation;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.PlaceInfo;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
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
 * 렌터카를 빌린 날의 이동 수단. 차가 있어도 걸어갈 거리는 걷고, 걷기 기준을 넘는 구간만 운전한다.
 */
class DrivingDayValidationTest {

    private final FakeTravelTimeProvider travel = FakeTravelTimeProvider.byDistance();
    private final ItineraryValidator validator =
            new ItineraryValidator(OsakaFixtures.places(), travel, ValidationPolicy.defaults());

    @Test
    void 렌터카를_빌린_날은_먼_구간을_운전으로_묻는다() {
        // 숙소(난바) → 우메다 스카이 약 4.5km. 걷기 기준 밖이다
        validate(DAY2, UMEDA_SKY);

        assertThat(travel.queries()).isNotEmpty()
                .allSatisfy(query -> assertThat(query.mode()).isEqualTo(TravelMode.DRIVE));
    }

    @Test
    void 렌터카를_빌린_날도_가까운_곳은_걸어서_묻는다() {
        // 숙소(난바) → 도톤보리 약 340m
        validate(DAY2, DOTONBORI);

        assertThat(travel.queries()).isNotEmpty()
                .allSatisfy(query -> assertThat(query.mode()).isEqualTo(TravelMode.WALK));
    }

    @Test
    void 렌터카가_없는_날은_먼_구간을_대중교통으로_묻는다() {
        // 같은 여행이지만 3일차에는 렌터카가 없다
        validate(DAY3, UMEDA_SKY);

        assertThat(travel.queries()).isNotEmpty()
                .allSatisfy(query -> assertThat(query.mode()).isEqualTo(TravelMode.TRANSIT));
    }

    private void validate(LocalDate date, PlaceInfo place) {
        ItineraryDraft draft = ItineraryDraft.of(DayDraft.of(date, VisitDraft.of("v-1", place.placeId())));
        validator.validate(draft, OsakaFixtures.tripWithRentalOnDay2(), PreferenceProfile.defaults(),
                ValidationOrigin.USER_EDITED);
    }
}
