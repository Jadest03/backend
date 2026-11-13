package com.dotlo.line4thon_18team.itinerary.web;

import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.place.OpeningHours;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 검증 API. MySQL이 필요하다 (docker compose up -d).
 * 구글 API를 부르지 않도록 BE2 영업시간·이동시간은 가짜로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class ItineraryCheckControllerTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 10);
    private static final LocalDate END = LocalDate.of(2026, 11, 12);

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository tripMembers;

    @MockitoBean OpeningHours openingHours;
    @MockitoBean TravelTimeProvider travelTimeProvider;

    private Long ownerId;
    private Long otherId;
    private Long companionId;
    private Long tripId;

    @BeforeEach
    void setUp() {
        User owner = users.save(User.builder().googleSub("sub-owner-itn").email("owner@test.com").name("주인").build());
        User other = users.save(User.builder().googleSub("sub-other-itn").email("other@test.com").name("남").build());
        Trip trip = trips.save(Trip.create(owner, "오사카", START, END, Companion.ALONE, null));
        User companion = users.save(
                User.builder().googleSub("sub-companion-itn").email("companion@test.com").name("동행").build());
        tripMembers.save(TripMember.create(trip, companion));
        ownerId = owner.getId();
        otherId = other.getId();
        companionId = companion.getId();
        tripId = trip.getId();
    }

    @Test
    void 동행자도_일정_검증을_쓸_수_있다() throws Exception {
        mvc.perform(check(companionId, """
                        {"origin": "USER_EDITED",
                         "days": [{"date": "2026-11-10",
                                   "visits": [{"itemId": "v1", "placeId": "ChIJ-a"}]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].visits[0].itemId").value("v1"));
    }

    @Test
    void 구성원이_아니면_일정_검증도_404다() throws Exception {
        mvc.perform(check(otherId, """
                        {"origin": "USER_EDITED",
                         "days": [{"date": "2026-11-10", "visits": []}]}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void 저장_장소에_없는_곳은_정보_없음으로_표시하고_계속_계산한다() throws Exception {
        mvc.perform(check(ownerId, """
                        {"origin": "USER_EDITED",
                         "days": [{"date": "2026-11-10",
                                   "visits": [{"itemId": "v1", "placeId": "ChIJ-unknown"}]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].visits.length()").value(1))
                .andExpect(jsonPath("$.days[0].visits[0].itemId").value("v1"))
                .andExpect(jsonPath("$.issues[?(@.code == 'PLACE_INFO_MISSING')]").exists())
                .andExpect(jsonPath("$.summary.valid").value(true));
    }

    @Test
    void 방문이_없으면_빈_시간표를_돌려준다() throws Exception {
        mvc.perform(check(ownerId, """
                        {"origin": "AI_GENERATED",
                         "days": [{"date": "2026-11-10", "visits": []}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].visits.length()").value(0))
                .andExpect(jsonPath("$.summary.errorCount").value(0));
    }

    @Test
    void 여행_기간_밖_날짜는_400이다() throws Exception {
        mvc.perform(check(ownerId, """
                        {"origin": "AI_GENERATED",
                         "days": [{"date": "2026-12-25",
                                   "visits": [{"itemId": "v1", "placeId": "ChIJ-x"}]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ITINERARY"));
    }

    @Test
    void itemId가_중복이면_400이다() throws Exception {
        mvc.perform(check(ownerId, """
                        {"origin": "AI_GENERATED",
                         "days": [{"date": "2026-11-10",
                                   "visits": [{"itemId": "v1", "placeId": "ChIJ-a"},
                                              {"itemId": "v1", "placeId": "ChIJ-b"}]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ITINERARY"));
    }

    @Test
    void origin이_없으면_400이다() throws Exception {
        mvc.perform(check(ownerId, """
                        {"days": [{"date": "2026-11-10", "visits": []}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeId가_없으면_400이다() throws Exception {
        mvc.perform(check(ownerId, """
                        {"origin": "AI_GENERATED",
                         "days": [{"date": "2026-11-10",
                                   "visits": [{"itemId": "v1"}]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void date가_없으면_400이다() throws Exception {
        mvc.perform(check(ownerId, """
                        {"origin": "AI_GENERATED",
                         "days": [{"visits": []}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void stayMinutes가_범위_밖이면_400이다() throws Exception {
        mvc.perform(check(ownerId, """
                        {"origin": "AI_GENERATED",
                         "days": [{"date": "2026-11-10",
                                   "visits": [{"itemId": "v1", "placeId": "ChIJ-a", "stayMinutes": 0}]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 남의_여행이면_404다() throws Exception {
        mvc.perform(check(otherId, """
                        {"origin": "AI_GENERATED",
                         "days": [{"date": "2026-11-10", "visits": []}]}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mvc.perform(post("/api/trips/{tripId}/itineraries/check", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"origin": "AI_GENERATED", "days": []}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    private org.springframework.test.web.servlet.RequestBuilder check(Long userId, String body) {
        return post("/api/trips/{tripId}/itineraries/check", tripId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
