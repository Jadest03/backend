package com.dotlo.line4thon_18team.member;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 초대로 참여한 구성원도 BE1 API(여행 수정, Takeout 업로드, 일정 조회/목록/되돌리기, 확정, 공유)를 방장과 똑같이 쓴다.
 * 기획 결정: 참여한 모든 멤버가 처음부터 같은 편집 권한. 구성원이 아니면 404. MySQL이 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class Be1MemberAccessTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);
    private static final String CSV = """
            제목,메모,URL,태그,댓글

            도톤보리,,https://www.google.com/maps/place/x/data=!4m2!3m1!1s0x6000e7130b0e1d3f:0x1d3f0b0e1300e760,,
            """;

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository members;
    @Autowired ItineraryService itineraryService;

    private Long ownerId;
    private Long memberId;
    private Long strangerId;
    private Long tripId;

    @BeforeEach
    void setUp() {
        User owner = users.save(newUser("owner"));
        User member = users.save(newUser("member"));
        strangerId = users.save(newUser("stranger")).getId();
        Trip trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.FRIEND, null));
        members.save(TripMember.create(trip, member));
        ownerId = owner.getId();
        memberId = member.getId();
        tripId = trip.getId();
    }

    @Test
    void 구성원은_여행_정보를_고친다() throws Exception {
        perform(patch("/api/trips/{tripId}", tripId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endDate\": \"2026-12-13\"}"), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value("2026-12-13"));
    }

    @Test
    void 구성원은_Takeout_파일을_올린다() throws Exception {
        perform(multipart("/api/trips/{tripId}/places/upload", tripId).file(csvFile()), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNewCount").value(1));
    }

    @Test
    void 구성원은_일정을_보고_되돌리고_확정하고_공유한다() throws Exception {
        saveItinerary(null);
        saveItinerary(1);

        perform(get("/api/trips/{tripId}/itineraries/latest", tripId), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        perform(get("/api/trips/{tripId}/itineraries", tripId), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        perform(post("/api/trips/{tripId}/itineraries/{version}/revert", tripId, 1), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
        perform(post("/api/trips/{tripId}/confirm", tripId), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        perform(post("/api/trips/{tripId}/share", tripId), memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void 구성원이_아니면_모두_404() throws Exception {
        saveItinerary(null);
        saveItinerary(1);
        List<AbstractMockHttpServletRequestBuilder<?>> requests = List.of(
                patch("/api/trips/{tripId}", tripId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\": \"2026-12-13\"}"),
                multipart("/api/trips/{tripId}/places/upload", tripId).file(csvFile()),
                get("/api/trips/{tripId}/itineraries/latest", tripId),
                get("/api/trips/{tripId}/itineraries", tripId),
                post("/api/trips/{tripId}/itineraries/{version}/revert", tripId, 1),
                post("/api/trips/{tripId}/confirm", tripId),
                post("/api/trips/{tripId}/share", tripId));
        for (AbstractMockHttpServletRequestBuilder<?> request : requests) {
            perform(request, strangerId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        }
    }

    private void saveItinerary(Integer baseVersion) {
        ItineraryDraft draft = ItineraryDraft.of(DayDraft.of(START, VisitDraft.of("v-3f9a1c20", "ChIJ-a")));
        itineraryService.save(tripId, ownerId, ItinerarySource.AI_GENERATED, baseVersion, draft,
                ValidationResult.of(List.of(), List.of()));
    }

    private static MockMultipartFile csvFile() {
        return new MockMultipartFile("file", "오사카.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request, Long userId) throws Exception {
        return mvc.perform(request.header("X-User-Id", userId));
    }

    private static User newUser(String role) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-be1-" + role + "-" + id).email(id + "@test.com").name(role).build();
    }
}
