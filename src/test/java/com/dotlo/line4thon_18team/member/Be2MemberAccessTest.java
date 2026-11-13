package com.dotlo.line4thon_18team.member;

import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.place.PlaceMatcher.Candidate;
import com.dotlo.line4thon_18team.place.PlaceMatcher.MatchResult;
import com.dotlo.line4thon_18team.place.PlaceMatcher.MatchStatus;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.reservation.FlightClient;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 초대로 참여한 구성원도 BE2 API(예약 정보, 장소 검색, 저장 장소 추가·보강·후보 선택·삭제)를 방장과 똑같이 쓴다.
 * 기획 결정: 참여한 모든 멤버가 처음부터 같은 편집 권한. 구성원이 아니면 404. MySQL이 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class Be2MemberAccessTest {

    private static final String APA = "ChIJapa";
    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository members;
    @Autowired SavedPlaceRepository savedPlaces;
    @MockitoBean PlaceMatcher placeMatcher;
    @MockitoBean FlightClient flightClient;

    private Long memberId;
    private Long strangerId;
    private Trip trip;

    @BeforeEach
    void setUp() {
        User owner = users.save(newUser("owner"));
        User member = users.save(newUser("member"));
        strangerId = users.save(newUser("stranger")).getId();
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.FRIEND, 3));
        members.save(TripMember.create(trip, member));
        memberId = member.getId();

        when(placeMatcher.details(null, APA)).thenReturn(new MatchResult(null, MatchStatus.MATCHED,
                new Candidate(APA, "APA 호텔 난바", "주소", 34.66, 135.50), List.of(), PlaceCategory.OTHER, false));
        when(placeMatcher.searchNear(anyString(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(
                new Candidate(APA, "APA 호텔 난바", "주소", 34.66, 135.50)));
    }

    @Test
    void 구성원은_예약_정보를_보고_저장한다() throws Exception {
        perform(get("/api/trips/{tripId}/reservations", trip.getId()), memberId)
                .andExpect(status().isOk());
        perform(put("/api/trips/{tripId}/reservations/stays", trip.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stays\":[{\"placeId\":\"" + APA + "\",\"checkIn\":\"2026-12-10\",\"checkOut\":\"2026-12-12\"}]}"), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays[0].name").value("APA 호텔 난바"));
    }

    @Test
    void 구성원은_장소를_검색한다() throws Exception {
        perform(get("/api/places/search").param("query", "APA").param("tripId", trip.getId().toString()), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placeId").value(APA));
    }

    @Test
    void 구성원은_저장_장소를_추가_보강_후보선택_삭제한다() throws Exception {
        perform(post("/api/trips/{tripId}/places", trip.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"" + APA + "\"}"), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].placeId").value(APA));
        perform(post("/api/trips/{tripId}/places/enrich", trip.getId()), memberId)
                .andExpect(status().isOk());

        SavedPlace pin = savedPlaces.save(SavedPlace.fromSearch(trip, "34.66, 135.50", "직접 추가"));
        pin.updateMatchStatus(PlaceMatchStatus.NEEDS_REVIEW);
        when(placeMatcher.details(pin.getId(), "ChIJnew")).thenReturn(new MatchResult(pin.getId(), MatchStatus.MATCHED,
                new Candidate("ChIJnew", "새 장소", "주소", 34.67, 135.50), List.of(), PlaceCategory.FOOD, false));
        perform(patch("/api/trips/{tripId}/places/{placeId}/match", trip.getId(), pin.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"placeId\":\"ChIJnew\"}"), memberId)
                .andExpect(status().isOk());

        perform(delete("/api/trips/{tripId}/places/{placeId}", trip.getId(), pin.getId()), memberId)
                .andExpect(status().isNoContent());
    }

    @Test
    void 구성원이_아니면_모두_404() throws Exception {
        Long tripId = trip.getId();
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/trips/{tripId}/reservations", tripId),
                put("/api/trips/{tripId}/reservations/rental", tripId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dates\":[]}"),
                get("/api/places/search").param("query", "APA").param("tripId", tripId.toString()),
                get("/api/trips/{tripId}/places", tripId),
                post("/api/trips/{tripId}/places", tripId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":\"" + APA + "\"}"),
                post("/api/trips/{tripId}/places/enrich", tripId),
                delete("/api/trips/{tripId}/places/{placeId}", tripId, 1L));
        for (MockHttpServletRequestBuilder request : requests) {
            perform(request, strangerId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        }
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Long userId) throws Exception {
        return mvc.perform(request.header("X-User-Id", userId));
    }

    private static User newUser(String role) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-be2-" + role + "-" + id).email(id + "@test.com").name(role).build();
    }
}
