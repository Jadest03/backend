package com.dotlo.line4thon_18team.itinerary.version;

import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 직접 고친 일정 저장 API. MySQL이 필요하다 (docker compose up -d).
 * 저장이 자기 트랜잭션을 따로 열어서 테스트 트랜잭션으로 되돌릴 수 없으므로, 만든 데이터는 테스트 끝에 지운다.
 * 구글 API를 부르지 않도록 영업시간/이동시간은 가짜로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
class ItineraryManualSaveTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 20);

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired ItineraryVersionRepository itineraryVersions;

    @MockitoBean OpeningHours openingHours;
    @MockitoBean TravelTimeProvider travelTimeProvider;

    private User owner;
    private User other;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser());
        other = users.save(newUser());
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.ALONE, null));
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
    void 처음_저장하면_버전1이고_새_방문에는_itemId를_만들어_준다() throws Exception {
        save(owner, """
                {"days": [{"date": "2026-11-20",
                           "visits": [{"itemId": "v-keep0001", "placeId": "ChIJ-a", "stayMinutes": 90},
                                      {"placeId": "ChIJ-b", "fixedStart": "18:00"}]}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.source").value("MANUAL_EDIT"))
                .andExpect(jsonPath("$.draft.days[0].visits[0].itemId").value("v-keep0001"))
                .andExpect(jsonPath("$.draft.days[0].visits[1].itemId").value(startsWith("v-")))
                .andExpect(jsonPath("$.validation.days[0].visits.length()").value(2));
    }

    @Test
    void 일정이_있는데_baseVersion이_없으면_409() throws Exception {
        save(owner, oneDay(null)).andExpect(status().isCreated());

        save(owner, oneDay(null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY_VERSION_CONFLICT"));
    }

    @Test
    void baseVersion이_최신이_아니면_409() throws Exception {
        save(owner, oneDay(null)).andExpect(status().isCreated());
        save(owner, oneDay(1)).andExpect(jsonPath("$.version").value(2));

        save(owner, oneDay(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY_VERSION_CONFLICT"));
    }

    @Test
    void 여행_기간_밖_날짜면_400() throws Exception {
        save(owner, """
                {"days": [{"date": "2026-12-25", "visits": [{"placeId": "ChIJ-a"}]}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ITINERARY"));
    }

    @Test
    void 체류시간이_범위_밖이면_400() throws Exception {
        save(owner, """
                {"days": [{"date": "2026-11-20", "visits": [{"placeId": "ChIJ-a", "stayMinutes": 0}]}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 남의_여행이면_404() throws Exception {
        save(other, oneDay(null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    private ResultActions save(User user, String body) throws Exception {
        return mvc.perform(post("/api/trips/{tripId}/itineraries", trip.getId())
                .header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String oneDay(Integer baseVersion) {
        return """
                {"baseVersion": %s,
                 "days": [{"date": "2026-11-20", "visits": [{"itemId": "v-keep0001", "placeId": "ChIJ-a"}]}]}"""
                .formatted(baseVersion);
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-" + id).email(id + "@test.com").name("테스트").build();
    }
}
