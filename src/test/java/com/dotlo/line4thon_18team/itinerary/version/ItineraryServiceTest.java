package com.dotlo.line4thon_18team.itinerary.version;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 저장. MySQL이 필요하다 (docker compose up -d).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class ItineraryServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 20);
    private static final ValidationResult VALID = ValidationResult.of(List.of(), List.of());

    @Autowired ItineraryService itineraryService;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;

    private long ownerId;
    private long otherId;
    private long tripId;

    @BeforeEach
    void setUp() {
        User owner = users.save(User.builder().googleSub("sub-owner").email("owner@test.com").name("주인").build());
        User other = users.save(User.builder().googleSub("sub-other").email("other@test.com").name("남").build());
        Trip trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.ALONE, null));
        ownerId = owner.getId();
        otherId = other.getId();
        tripId = trip.getId();
    }

    @Test
    void 저장할_때마다_버전이_하나씩_올라간다() {
        ItineraryVersionView v1 = itineraryService.save(tripId, ownerId, ItinerarySource.AI_GENERATED, null, draft("v-aaaa0001"), VALID);
        ItineraryVersionView v2 = itineraryService.save(tripId, ownerId, ItinerarySource.AI_EDITED, 1, draft("v-aaaa0002"), VALID);

        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.baseVersion()).isEqualTo(1);
        assertThat(itineraryService.findLatest(tripId)).get().extracting(ItineraryVersionView::version).isEqualTo(2);
    }

    @Test
    void 저장한_일정을_itemId까지_그대로_읽는다() {
        ItineraryDraft draft = draft("v-3f9a1c20");
        itineraryService.save(tripId, ownerId, ItinerarySource.AI_GENERATED, null, draft, VALID);

        ItineraryVersionView latest = itineraryService.findLatest(tripId).orElseThrow();

        assertThat(latest.draft()).isEqualTo(draft);
        assertThat(latest.source()).isEqualTo(ItinerarySource.AI_GENERATED);
        assertThat(latest.createdBy()).isEqualTo(ownerId);
        assertThat(latest.validation()).contains("\"summary\"");
    }

    @Test
    void baseVersion이_최신이_아니면_409() {
        itineraryService.save(tripId, ownerId, ItinerarySource.AI_GENERATED, null, draft("v-aaaa0001"), VALID);
        itineraryService.save(tripId, ownerId, ItinerarySource.MANUAL_EDIT, 1, draft("v-aaaa0002"), VALID);

        assertThatThrownBy(() -> itineraryService.save(tripId, ownerId, ItinerarySource.AI_EDITED, 1, draft("v-aaaa0003"), VALID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITINERARY_VERSION_CONFLICT);
    }

    @Test
    void 남의_여행이면_저장할_수_없다() {
        assertThatThrownBy(() -> itineraryService.save(tripId, otherId, ItinerarySource.AI_GENERATED, null, draft("v-aaaa0001"), VALID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    void 최신_일정_API는_일정이_없으면_404_있으면_검증결과를_JSON_객체로_준다() throws Exception {
        mvc.perform(get("/api/trips/{tripId}/itineraries/latest", tripId).header("X-User-Id", ownerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY_NOT_FOUND"));

        itineraryService.save(tripId, ownerId, ItinerarySource.AI_GENERATED, null, draft("v-3f9a1c20"), VALID);

        mvc.perform(get("/api/trips/{tripId}/itineraries/latest", tripId).header("X-User-Id", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.draft.days[0].visits[0].itemId").value("v-3f9a1c20"))
                .andExpect(jsonPath("$.validation.summary.valid").value(true));
    }

    private static ItineraryDraft draft(String itemId) {
        return ItineraryDraft.of(DayDraft.of(START,
                VisitDraft.of(itemId, "ChIJt9Y375HmAGARejKVNQwlkbI").withStayMinutes(90).withFixedStart(LocalTime.of(10, 0))));
    }
}
