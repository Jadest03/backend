package com.dotlo.line4thon_18team.itinerary.validation;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelQuery;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakeTravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DOTONBORI;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.HOTEL_NAMBA;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.HOTEL_UMEDA;
import static org.assertj.core.api.Assertions.assertThat;

/** 하루를 어느 숙소에서 출발해 어느 숙소로 끝내는지. 이동시간 조회에 실제로 반영되는지 본다. */
class NightlyStayValidationTest {

    private final FakeTravelTimeProvider travel = FakeTravelTimeProvider.byDistance();
    private final ItineraryValidator validator =
            new ItineraryValidator(OsakaFixtures.places(), travel, ValidationPolicy.defaults());

    @Test
    void 숙소를_옮기는_날은_전날_숙소에서_출발해_그날_숙소로_돌아간다() {
        validator.validate(oneVisitOn(OsakaFixtures.DAY3), OsakaFixtures.tripMovingToUmeda(),
                PreferenceProfile.defaults(), ValidationOrigin.USER_EDITED);

        List<TravelQuery> queries = travel.queries();
        assertThat(queries.get(0).from()).isEqualTo(HOTEL_NAMBA);
        assertThat(queries.get(queries.size() - 1).to()).isEqualTo(HOTEL_UMEDA);
    }

    @Test
    void 같은_숙소에_머무는_날은_출발지와_도착지가_같다() {
        validator.validate(oneVisitOn(OsakaFixtures.DAY2), OsakaFixtures.tripMovingToUmeda(),
                PreferenceProfile.defaults(), ValidationOrigin.USER_EDITED);

        List<TravelQuery> queries = travel.queries();
        assertThat(queries.get(0).from()).isEqualTo(HOTEL_NAMBA);
        assertThat(queries.get(queries.size() - 1).to()).isEqualTo(HOTEL_NAMBA);
    }

    @Test
    void 숙소를_입력하지_않으면_기준점에서_출발해_기준점으로_돌아간다() {
        validator.validate(oneVisitOn(OsakaFixtures.DAY3), OsakaFixtures.trip(),
                PreferenceProfile.defaults(), ValidationOrigin.USER_EDITED);

        List<TravelQuery> queries = travel.queries();
        assertThat(queries.get(0).from()).isEqualTo(HOTEL_NAMBA);
        assertThat(queries.get(queries.size() - 1).to()).isEqualTo(HOTEL_NAMBA);
    }

    private static ItineraryDraft oneVisitOn(java.time.LocalDate date) {
        return ItineraryDraft.of(DayDraft.of(date, VisitDraft.of("v-1", DOTONBORI.placeId())));
    }
}
