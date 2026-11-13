package com.dotlo.line4thon_18team.itinerary.version;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.result.DayTimeline;
import com.dotlo.line4thon_18team.itinerary.validation.result.DayWindow;
import com.dotlo.line4thon_18team.itinerary.validation.result.IssueCode;
import com.dotlo.line4thon_18team.itinerary.validation.result.IssueKind;
import com.dotlo.line4thon_18team.itinerary.validation.result.LegSource;
import com.dotlo.line4thon_18team.itinerary.validation.result.ScheduledVisit;
import com.dotlo.line4thon_18team.itinerary.validation.result.Severity;
import com.dotlo.line4thon_18team.itinerary.validation.result.TravelLeg;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationIssue;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.validation.result.WindowAdjustment;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 되돌리기 API. MySQL이 필요하다 (docker compose up -d).
 * 저장이 자기 트랜잭션을 따로 열어서 테스트 트랜잭션으로 되돌릴 수 없으므로, 만든 데이터는 테스트 끝에 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
class ItineraryRevertApiTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 27);

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
    void 되돌리면_그_버전의_내용이_새_버전으로_쌓인다() throws Exception {
        saveWithVisit(ItinerarySource.AI_GENERATED, null, "v-aaa");   // v1
        saveEmpty(ItinerarySource.MANUAL_EDIT, 1);                    // v2 — 방문을 지운 상태

        revert(owner, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.source").value("REVERT"))
                .andExpect(jsonPath("$.baseVersion").value(2))
                .andExpect(jsonPath("$.draft.days[0].visits.length()").value(1))
                .andExpect(jsonPath("$.draft.days[0].visits[0].itemId").value("v-aaa"))
                .andExpect(jsonPath("$.validation.summary").exists());

        // 되돌린 결과가 최신 일정이 된다. v1·v2는 지워지지 않는다
        mvc.perform(get("/api/trips/{tripId}/itineraries/latest", trip.getId())
                        .header("X-User-Id", owner.getId()))
                .andExpect(jsonPath("$.version").value(3));
        mvc.perform(get("/api/trips/{tripId}/itineraries", trip.getId())
                        .header("X-User-Id", owner.getId()))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void 계산된_시각과_문제_목록이_든_버전도_그대로_복원된다() throws Exception {
        saveWithTimeline(ItinerarySource.AI_GENERATED, null);   // v1
        saveEmpty(ItinerarySource.MANUAL_EDIT, 1);              // v2

        revert(owner, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.source").value("REVERT"))
                // 하루 창과 조정 사유 (Set<enum>)
                .andExpect(jsonPath("$.validation.days[0].window.start", startsWith("2026-11-27T10:30")))
                .andExpect(jsonPath("$.validation.days[0].window.adjustments[0]").value("SHORTENED_BY_ARRIVAL"))
                // 이동 구간. estimated 는 TravelLeg.isEstimated() 가 만드는 파생 값이라
                // 직렬화에는 들어가고 다시 읽을 때는 무시돼야 한다
                .andExpect(jsonPath("$.validation.days[0].visits[0].travel.mode").value("WALK"))
                .andExpect(jsonPath("$.validation.days[0].visits[0].travel.source").value("ESTIMATED"))
                .andExpect(jsonPath("$.validation.days[0].visits[0].travel.estimated").value(true))
                .andExpect(jsonPath("$.validation.days[0].visits[0].waitMinutes").value(18))
                .andExpect(jsonPath("$.validation.days[0].visits[0].stayMinutes").value(90))
                .andExpect(jsonPath("$.validation.days[0].returnLeg.minutes").value(25))
                .andExpect(jsonPath("$.validation.days[0].returnLeg.estimated").value(false))
                .andExpect(jsonPath("$.validation.days[0].endsAt", startsWith("2026-11-27T12:55")))
                // 문제 목록과 params (Map<String, Object>)
                .andExpect(jsonPath("$.validation.issues[0].code").value("CLOSED_ON_DAY"))
                .andExpect(jsonPath("$.validation.issues[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.validation.issues[0].params.dayOfWeek").value("MONDAY"))
                // 저장 당시 계산해 둔 요약
                .andExpect(jsonPath("$.validation.summary.valid").value(true))
                .andExpect(jsonPath("$.validation.summary.warningCount").value(1))
                .andExpect(jsonPath("$.validation.summary.placesNeedingCheck").value(1));
    }

    @Test
    void 되돌리기를_다시_되돌릴_수_있다() throws Exception {
        saveWithVisit(ItinerarySource.AI_GENERATED, null, "v-aaa");   // v1
        saveEmpty(ItinerarySource.MANUAL_EDIT, 1);                    // v2
        revert(owner, 1).andExpect(status().isOk());                  // v3 = v1 내용

        revert(owner, 2).andExpect(status().isOk())                   // v4 = v2 내용
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.source").value("REVERT"))
                .andExpect(jsonPath("$.baseVersion").value(3))
                .andExpect(jsonPath("$.draft.days[0].visits.length()").value(0));
    }

    @Test
    void 최신_버전으로_되돌리면_400이다() throws Exception {
        saveWithVisit(ItinerarySource.AI_GENERATED, null, "v-aaa");   // v1

        revert(owner, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY_ALREADY_LATEST"));
    }

    @Test
    void 없는_버전으로_되돌리면_404다() throws Exception {
        saveWithVisit(ItinerarySource.AI_GENERATED, null, "v-aaa");   // v1

        revert(owner, 9).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY_VERSION_NOT_FOUND"));
    }

    @Test
    void 남의_여행이면_404다() throws Exception {
        saveWithVisit(ItinerarySource.AI_GENERATED, null, "v-aaa");
        saveEmpty(ItinerarySource.MANUAL_EDIT, 1);

        revert(other, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    /**
     * 검증기가 실제로 만드는 모양의 검증 결과를 저장한다. 되돌리기가 이 JSON을 ValidationResult 로
     * 다시 읽기 때문에, 파생 값(TravelLeg.estimated)·Set·Map 이 섞인 상태로 넣어 둔다.
     */
    private void saveWithTimeline(ItinerarySource source, Integer baseVersion) {
        LocalDateTime dayStart = START.atTime(10, 30);
        ScheduledVisit visit = new ScheduledVisit("v-aaa", "ChIJ-a",
                new TravelLeg(TravelMode.WALK, 12, 900, LegSource.ESTIMATED),
                dayStart.plusMinutes(12), dayStart.plusMinutes(30), dayStart.plusMinutes(120), 18, 90);
        DayTimeline day = new DayTimeline(START,
                new DayWindow(START, dayStart, START.atTime(22, 0), Set.of(WindowAdjustment.SHORTENED_BY_ARRIVAL)),
                List.of(visit),
                new TravelLeg(TravelMode.TRANSIT, 25, 5200, LegSource.ROUTES_API),
                dayStart.plusMinutes(145));
        ValidationIssue issue = new ValidationIssue(IssueCode.CLOSED_ON_DAY, Severity.WARNING,
                IssueKind.SCHEDULE_CONFLICT, START, "v-aaa", "ChIJ-a", Map.of("dayOfWeek", "MONDAY"));

        itineraryService.save(trip.getId(), owner.getId(), source, baseVersion,
                ItineraryDraft.of(DayDraft.of(START, VisitDraft.of("v-aaa", "ChIJ-a"))),
                ValidationResult.of(List.of(day), List.of(issue)));
    }

    private void saveWithVisit(ItinerarySource source, Integer baseVersion, String itemId) {
        itineraryService.save(trip.getId(), owner.getId(), source, baseVersion,
                ItineraryDraft.of(DayDraft.of(START, VisitDraft.of(itemId, "ChIJ-a"))),
                ValidationResult.of(List.of(), List.of()));
    }

    private void saveEmpty(ItinerarySource source, Integer baseVersion) {
        itineraryService.save(trip.getId(), owner.getId(), source, baseVersion,
                ItineraryDraft.of(DayDraft.of(START)),
                ValidationResult.of(List.of(), List.of()));
    }

    private ResultActions revert(User user, int version) throws Exception {
        return mvc.perform(post("/api/trips/{tripId}/itineraries/{version}/revert", trip.getId(), version)
                .header("X-User-Id", user.getId()));
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-rev-" + id).email(id + "@test.com").name("되돌리기테스트").build();
    }
}
