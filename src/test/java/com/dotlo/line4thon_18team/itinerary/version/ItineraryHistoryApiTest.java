package com.dotlo.line4thon_18team.itinerary.version;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.place.OpeningHours;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 변경 이력 조회 API. MySQL이 필요하다 (docker compose up -d).
 * 저장이 자기 트랜잭션을 따로 열어서 테스트 트랜잭션으로 되돌릴 수 없으므로, 만든 데이터는 테스트 끝에 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
class ItineraryHistoryApiTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 26);

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired ItineraryVersionRepository itineraryVersions;
    @Autowired ItineraryService itineraryService;

    @MockitoBean OpeningHours openingHours;
    @MockitoBean TravelTimeProvider travelTimeProvider;

    private User owner;
    private User other;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser());
        other = users.save(newUser());
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(1), Companion.ALONE, null));
    }

    @AfterEach
    void tearDown() {
        itineraryVersions.deleteAll(itineraryVersions.findAll().stream()
                .filter(itinerary -> itinerary.getTrip().getId().equals(trip.getId()))
                .toList());
        trips.delete(trip);
        users.delete(owner);
        users.delete(other);
    }

    @Test
    void 최신_버전부터_내려주고_일정_내용은_담지_않는다() throws Exception {
        save(ItinerarySource.AI_GENERATED, null);
        save(ItinerarySource.MANUAL_EDIT, 1);
        save(ItinerarySource.AI_EDITED, 2);

        history(owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].version").value(3))
                .andExpect(jsonPath("$[0].source").value("AI_EDITED"))
                .andExpect(jsonPath("$[0].baseVersion").value(2))
                .andExpect(jsonPath("$[0].createdBy").value(owner.getId()))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[2].version").value(1))
                .andExpect(jsonPath("$[2].source").value("AI_GENERATED"))
                .andExpect(jsonPath("$[2].baseVersion").doesNotExist())
                .andExpect(jsonPath("$[0].draft").doesNotExist())
                .andExpect(jsonPath("$[0].validation").doesNotExist());
    }

    @Test
    void 저장된_일정이_없으면_빈_목록이다() throws Exception {
        history(owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 남의_여행이면_404다() throws Exception {
        save(ItinerarySource.AI_GENERATED, null);

        history(other).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    private void save(ItinerarySource source, Integer baseVersion) {
        itineraryService.save(trip.getId(), owner.getId(), source, baseVersion,
                ItineraryDraft.of(DayDraft.of(START, VisitDraft.of("v-aaa", "ChIJ-a"))),
                ValidationResult.of(List.of(), List.of()));
    }

    private ResultActions history(User user) throws Exception {
        return mvc.perform(get("/api/trips/{tripId}/itineraries", trip.getId())
                .header("X-User-Id", user.getId()));
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-hist-" + id).email(id + "@test.com").name("이력테스트").build();
    }
}
