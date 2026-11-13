package com.dotlo.line4thon_18team.share;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.IssueCode;
import com.dotlo.line4thon_18team.itinerary.validation.result.IssueKind;
import com.dotlo.line4thon_18team.itinerary.validation.result.Severity;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationIssue;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.share.service.ShareService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 확정, 공유 링크, 공유된 일정 조회. MySQL이 필요하다 (docker compose up -d).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class ShareControllerTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 20);
    private static final String PLACE_ID = "ChIJt9Y375HmAGARejKVNQwlkbI";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired SavedPlaceRepository savedPlaces;
    @Autowired ItineraryService itineraryService;
    @MockitoSpyBean ShareService shareService;

    private Long ownerId;
    private Long otherId;
    private Long tripId;

    @BeforeEach
    void setUp() {
        User owner = users.save(User.builder().googleSub("sub-owner-share").email("owner@test.com").name("주인").build());
        User other = users.save(User.builder().googleSub("sub-other-share").email("other@test.com").name("남").build());
        Trip trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.ALONE, null));
        ownerId = owner.getId();
        otherId = other.getId();
        tripId = trip.getId();

        SavedPlace place = SavedPlace.fromTakeout(trip, null, "도톤보리", "꼭 가기", null, null, null, "오사카");
        place.applyMatch("도톤보리", PLACE_ID, "일본 오사카부 오사카시 주오구", 34.6687, 135.5013,
                PlaceCategory.SIGHTSEEING, PlaceMatchStatus.MATCHED);
        savedPlaces.save(place);
    }

    @Test
    void 일정이_없으면_확정할_수_없다() throws Exception {
        perform(post("/api/trips/{tripId}/confirm", tripId), ownerId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY_REQUIRED"));
    }

    @Test
    void 확정하면_CONFIRMED가_되고_확인이_필요한_장소_수를_알려준다() throws Exception {
        saveItinerary();

        perform(post("/api/trips/{tripId}/confirm", tripId), ownerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.placesNeedingCheck").value(1));
    }

    @Test
    void 확정하지_않은_여행은_공유할_수_없다() throws Exception {
        saveItinerary();

        perform(post("/api/trips/{tripId}/share", tripId), ownerId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_CONFIRMED"));
    }

    @Test
    void 공유_링크는_다시_요청해도_같은_토큰이다() throws Exception {
        String first = confirmAndShare();

        perform(post("/api/trips/{tripId}/share", tripId), ownerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(first));
    }

    @Test
    void 공유를_동시에_눌러_저장이_겹쳐도_먼저_만든_토큰을_준다() throws Exception {
        String first = confirmAndShare();
        // 다른 요청이 먼저 링크를 만들어서 이 요청의 저장이 trip_id UNIQUE 에 걸린 상황
        // share() 를 스텁해서 컨트롤러가 다시 읽는지만 본다. 실제 UNIQUE 위반 → 롤백 → 재조회 경로는 검증 범위 밖이다
        doThrow(new DataIntegrityViolationException("uk_trip_share_trip_id")).when(shareService).share(tripId, ownerId);

        perform(post("/api/trips/{tripId}/share", tripId), ownerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(first));
    }

    @Test
    void 공유된_일정은_로그인_없이_보이고_사용자_정보와_메모는_없다() throws Exception {
        String token = confirmAndShare();

        mvc.perform(get("/api/share/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.city").value("오사카"))
                .andExpect(jsonPath("$.itinerary.version").value(1))
                .andExpect(jsonPath("$.itinerary.draft.days[0].visits[0].placeId").value(PLACE_ID))
                .andExpect(jsonPath("$.itinerary.validation.summary").exists())
                .andExpect(jsonPath("$.places[0].name").value("도톤보리"))
                .andExpect(jsonPath("$.places[0].note").doesNotExist())
                .andExpect(jsonPath("$.itinerary.createdBy").doesNotExist());
    }

    @Test
    void 없는_토큰이면_404() throws Exception {
        mvc.perform(get("/api/share/{token}", "no-such-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_FOUND"));
    }

    @Test
    void 남의_여행은_확정할_수_없다() throws Exception {
        saveItinerary();

        perform(post("/api/trips/{tripId}/confirm", tripId), otherId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    private void saveItinerary() {
        ItineraryDraft draft = ItineraryDraft.of(DayDraft.of(START, VisitDraft.of("v-3f9a1c20", PLACE_ID)));
        ValidationIssue issue = new ValidationIssue(IssueCode.HOURS_UNKNOWN, Severity.WARNING, IssueKind.INFO_MISSING,
                START, "v-3f9a1c20", PLACE_ID, Map.of());
        itineraryService.save(tripId, ownerId, ItinerarySource.AI_GENERATED, null, draft,
                ValidationResult.of(List.of(), List.of(issue)));
    }

    private String confirmAndShare() throws Exception {
        saveItinerary();
        perform(post("/api/trips/{tripId}/confirm", tripId), ownerId).andExpect(status().isOk());
        String body = perform(post("/api/trips/{tripId}/share", tripId), ownerId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("token").asString();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Long userId) throws Exception {
        return mvc.perform(request.header("X-User-Id", userId));
    }
}
