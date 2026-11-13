package com.dotlo.line4thon_18team.itinerary.web;

import com.dotlo.line4thon_18team.global.llm.LlmClient;
import com.dotlo.line4thon_18team.global.llm.LlmResponse;
import com.dotlo.line4thon_18team.itinerary.edit.repository.ItineraryEditRequestRepository;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.place.OpeningHours;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 텍스트 수정 요청 → 적용 (EDIT-TEXT-001, EDIT-APPLY-001). MySQL이 필요하다 (docker compose up -d).
 * 수정안 보관과 일정 저장이 자기 트랜잭션을 따로 열어서 테스트 트랜잭션으로 되돌릴 수 없으므로,
 * 만든 데이터는 테스트 끝에 지운다. 구글 API와 AI는 가짜로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
class ItineraryEditApiTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 24);

    /** 2일차를 비우는 수정안 */
    private static final String SECOND_DAY_EMPTIED = """
            {"days": [{"dayNumber": 1, "visits": [{"itemId": "v-aaa", "placeNumber": null}]},
                      {"dayNumber": 2, "visits": []}],
             "reason": "구로몬 시장을 뺐습니다."}
            """;

    /** 1일차를 비우는 수정안 */
    private static final String FIRST_DAY_EMPTIED = """
            {"days": [{"dayNumber": 1, "visits": []},
                      {"dayNumber": 2, "visits": [{"itemId": "v-bbb", "placeNumber": null}]}],
             "reason": "도톤보리를 뺐습니다."}
            """;

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired ItineraryVersionRepository itineraryVersions;
    @Autowired ItineraryEditRequestRepository editRequests;
    @Autowired ItineraryService itineraryService;
    @Autowired TripMemberRepository tripMembers;

    @MockitoBean OpeningHours openingHours;
    @MockitoBean TravelTimeProvider travelTimeProvider;
    @MockitoBean LlmClient llmClient;

    private User owner;
    private User other;
    private User companion;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser());
        other = users.save(newUser());
        companion = users.save(newUser());
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(1), Companion.ALONE, null));
        tripMembers.save(TripMember.create(trip, companion));
        itineraryService.save(trip.getId(), owner.getId(), ItinerarySource.AI_GENERATED, null,
                ItineraryDraft.of(
                        DayDraft.of(START, VisitDraft.of("v-aaa", "ChIJ-dotonbori")),
                        DayDraft.of(START.plusDays(1), VisitDraft.of("v-bbb", "ChIJ-kuromon"))),
                ValidationResult.of(List.of(), List.of()));
        aiReturns(SECOND_DAY_EMPTIED);
    }

    @AfterEach
    void tearDown() {
        editRequests.deleteAll(editRequests.findAll().stream()
                .filter(edit -> edit.getTrip().getId().equals(trip.getId()))
                .toList());
        itineraryVersions.deleteAll(itineraryVersions.findAll().stream()
                .filter(itinerary -> itinerary.getTrip().getId().equals(trip.getId()))
                .toList());
        tripMembers.deleteAll(tripMembers.findAllByTripIdOrderByIdAsc(trip.getId()));
        trips.delete(trip);
        users.delete(owner);
        users.delete(other);
        users.delete(companion);
    }

    @Test
    void 수정_요청하면_변경점과_이유를_주고_현재_일정은_그대로다() throws Exception {
        requestEdit(owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.baseVersion").value(1))
                .andExpect(jsonPath("$.reason").value("구로몬 시장을 뺐습니다."))
                .andExpect(jsonPath("$.itinerary.draft.days.length()").value(2))
                .andExpect(jsonPath("$.itinerary.draft.days[0].visits[0].itemId").value("v-aaa"))
                .andExpect(jsonPath("$.itinerary.draft.days[1].visits.length()").value(0))
                .andExpect(jsonPath("$.itinerary.validation.summary").exists())
                .andExpect(jsonPath("$.diff.changes.length()").value(1))
                .andExpect(jsonPath("$.diff.changes[0].kind").value("REMOVED"))
                .andExpect(jsonPath("$.diff.changes[0].itemId").value("v-bbb"))
                .andExpect(jsonPath("$.diff.reorderedDays").isArray())
                .andExpect(jsonPath("$.diff.empty").doesNotExist());

        // 아직 적용하지 않았으므로 최신 일정은 v1 그대로다
        mvc.perform(get("/api/trips/{tripId}/itineraries/latest", trip.getId())
                        .header("X-User-Id", owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.draft.days[1].visits.length()").value(1));
    }

    @Test
    void 적용하면_AI_EDITED_새_버전이_된다() throws Exception {
        requestEdit(owner);

        apply(owner, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.source").value("AI_EDITED"))
                .andExpect(jsonPath("$.baseVersion").value(1))
                .andExpect(jsonPath("$.draft.days[1].visits.length()").value(0))
                .andExpect(jsonPath("$.validation.summary").exists());
    }

    @Test
    void 여러_번_요청하면_마지막_수정안이_적용된다() throws Exception {
        requestEdit(owner);
        aiReturns(FIRST_DAY_EMPTIED);
        requestEdit(owner);

        apply(owner, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.days[0].visits.length()").value(0))
                .andExpect(jsonPath("$.draft.days[1].visits[0].itemId").value("v-bbb"));
    }

    @Test
    void 적용한_뒤_같은_버전으로_다시_적용하면_409다() throws Exception {
        requestEdit(owner);
        apply(owner, 1).andExpect(status().isOk());

        apply(owner, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY_VERSION_CONFLICT"));
    }

    @Test
    void 수정안이_없는_버전으로_적용하면_404다() throws Exception {
        apply(owner, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EDIT_REQUEST_NOT_FOUND"));
    }

    @Test
    void 동행자도_수정_요청과_적용을_할_수_있다() throws Exception {
        requestEdit(companion).andExpect(status().isOk())
                .andExpect(jsonPath("$.baseVersion").value(1))
                .andExpect(jsonPath("$.reason").value("구로몬 시장을 뺐습니다."));

        apply(companion, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.source").value("AI_EDITED"))
                .andExpect(jsonPath("$.createdBy").value(companion.getId()));
    }

    @Test
    void 남의_여행이면_404이고_AI를_부르지_않는다() throws Exception {
        requestEdit(other).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));

        Mockito.verify(llmClient, Mockito.never()).complete(Mockito.any());
    }

    @Test
    void 수정_요청이_비어_있으면_400이고_AI를_부르지_않는다() throws Exception {
        mvc.perform(post("/api/trips/{tripId}/itineraries/edit-requests", trip.getId())
                        .header("X-User-Id", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        Mockito.verify(llmClient, Mockito.never()).complete(Mockito.any());
    }

    private void aiReturns(String json) {
        Mockito.when(llmClient.complete(Mockito.any())).thenReturn(new LlmResponse(json, null, "stop"));
    }

    private ResultActions requestEdit(User user) throws Exception {
        return mvc.perform(post("/api/trips/{tripId}/itineraries/edit-requests", trip.getId())
                .header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"둘째 날 하나 빼 주세요\"}"));
    }

    private ResultActions apply(User user, int version) throws Exception {
        return mvc.perform(post("/api/trips/{tripId}/itineraries/{version}/apply", trip.getId(), version)
                .header("X-User-Id", user.getId()));
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-edit-" + id).email(id + "@test.com").name("수정테스트").build();
    }
}
