package com.dotlo.line4thon_18team.chat.poll;

import com.dotlo.line4thon_18team.chat.poll.ai.PollPickInput;
import com.dotlo.line4thon_18team.chat.poll.ai.PollPickOutput;
import com.dotlo.line4thon_18team.chat.poll.ai.PollPickTask;
import com.dotlo.line4thon_18team.chat.poll.ai.PollSearchInput;
import com.dotlo.line4thon_18team.chat.poll.ai.PollSearchOutput;
import com.dotlo.line4thon_18team.chat.poll.ai.PollSearchTask;
import com.dotlo.line4thon_18team.chat.poll.dto.NewCandidate;
import com.dotlo.line4thon_18team.chat.poll.dto.PollCandidateCommand;
import com.dotlo.line4thon_18team.chat.poll.dto.PollView;
import com.dotlo.line4thon_18team.chat.poll.entity.PollStatus;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.global.llm.LlmResult;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItineraryVersion;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import com.dotlo.line4thon_18team.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 투표 후보 만들기 조립 (COLLAB-POLL-002): 검색어 → 검색 → 고르기 → 투표 생성.
 * 프롬프트와 출력 검사는 {@link com.dotlo.line4thon_18team.chat.poll.ai.PollCandidateTaskTest}에서 본다.
 */
class PollCandidateServiceTest {

    private static final long TRIP = 1L;
    private static final long USER = 7L;
    private static final LocalDate DAY1 = LocalDate.of(2026, 12, 14);

    private final TripService tripService = Mockito.mock(TripService.class);
    private final PlaceMatcher placeMatcher = Mockito.mock(PlaceMatcher.class);
    private final ItineraryVersionRepository versions = Mockito.mock(ItineraryVersionRepository.class);
    private final PollService pollService = Mockito.mock(PollService.class);
    private final StructuredLlmExecutor llm = Mockito.mock(StructuredLlmExecutor.class);

    private final PollCandidateService service = new PollCandidateService(
            tripService, placeMatcher, versions, pollService, llm,
            new PollSearchTask(new SchemaLoader()), new PollPickTask(new SchemaLoader()),
            JsonMapper.builder().build());

    @BeforeEach
    void setUp() {
        User user = User.builder().googleSub("sub").email("a@b.com").name("주인").build();
        Trip trip = Trip.create(user, "오사카", DAY1, DAY1.plusDays(3), Companion.FRIEND, null);
        Mockito.when(tripService.findMemberTrip(USER, TRIP)).thenReturn(trip);
        Mockito.when(versions.findFirstByTripIdOrderByVersionDesc(TRIP)).thenReturn(Optional.empty());
        Mockito.when(pollService.create(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new PollView(7L, "2일차에 어디 갈까요?", PollStatus.OPEN, 3, 0, List.of()));
    }

    @Test
    void 검색어마다_찾고_같은_장소는_한_번만_넘긴다() {
        givenQueries("오사카 무료 공원", "오사카 수족관");
        givenFound("오사카 무료 공원", place("p-castle", "오사카성 공원"), place("p-park", "나카노시마 공원"));
        givenFound("오사카 수족관", place("p-castle", "오사카성 공원"), place("p-aqua", "가이유칸"));
        givenPicks(1, 2, 3);

        service.create(TRIP, USER, command());

        ArgumentCaptor<PollPickInput> input = ArgumentCaptor.forClass(PollPickInput.class);
        Mockito.verify(llm).execute(Mockito.any(PollPickTask.class), input.capture());
        assertThat(input.getValue().places()).extracting(PollPickInput.Found::name)
                .containsExactly("오사카성 공원", "나카노시마 공원", "가이유칸");
    }

    @Test
    void 이미_일정에_있는_장소는_후보에서_뺀다() {
        givenItinerary("p-castle");
        givenQueries("오사카 무료 공원");
        givenFound("오사카 무료 공원", place("p-castle", "오사카성 공원"), place("p-park", "나카노시마 공원"),
                place("p-aqua", "가이유칸"));
        givenPicks(1, 2);

        service.create(TRIP, USER, command());

        ArgumentCaptor<PollPickInput> input = ArgumentCaptor.forClass(PollPickInput.class);
        Mockito.verify(llm).execute(Mockito.any(PollPickTask.class), input.capture());
        assertThat(input.getValue().places()).extracting(PollPickInput.Found::name)
                .containsExactly("나카노시마 공원", "가이유칸");
    }

    @Test
    void 고른_번호를_placeId로_되돌려_투표를_만든다() {
        givenQueries("오사카 수족관");
        givenFound("오사카 수족관", place("p-castle", "오사카성 공원"), place("p-aqua", "가이유칸"),
                place("p-wheel", "덴포잔 대관람차"));
        Mockito.when(llm.execute(Mockito.any(PollPickTask.class), Mockito.any(PollPickInput.class)))
                .thenReturn(new LlmResult<>(new PollPickOutput(List.of(
                        new PollPickOutput.Pick(2, "수족관"),
                        new PollPickOutput.Pick(3, "전망대"),
                        new PollPickOutput.Pick(1, "공원"))), 1));

        service.create(TRIP, USER, command());

        ArgumentCaptor<List<NewCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        Mockito.verify(pollService).create(Mockito.eq(TRIP), Mockito.eq(USER),
                Mockito.eq("2일차에 어디 갈까요?"), candidates.capture());
        assertThat(candidates.getValue()).extracting(NewCandidate::placeId, NewCandidate::tagline)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("p-aqua", "수족관"),
                        org.assertj.core.groups.Tuple.tuple("p-wheel", "전망대"),
                        org.assertj.core.groups.Tuple.tuple("p-castle", "공원"));
    }

    @Test
    void 찾은_곳이_하나뿐이면_400이고_고르기를_부르지_않는다() {
        givenQueries("오사카 무료 공원");
        givenFound("오사카 무료 공원", place("p-castle", "오사카성 공원"));

        assertThatThrownBy(() -> service.create(TRIP, USER, command()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_POLL_CANDIDATES);

        Mockito.verify(llm, Mockito.never()).execute(Mockito.any(PollPickTask.class), Mockito.any());
        Mockito.verifyNoInteractions(pollService);
    }

    @Test
    void 찾은_곳이_둘뿐이면_둘만_고르게_한다() {
        givenQueries("오사카 무료 공원");
        givenFound("오사카 무료 공원", place("p-castle", "오사카성 공원"), place("p-park", "나카노시마 공원"));
        givenPicks(1, 2);

        service.create(TRIP, USER, command());

        ArgumentCaptor<PollPickInput> input = ArgumentCaptor.forClass(PollPickInput.class);
        Mockito.verify(llm).execute(Mockito.any(PollPickTask.class), input.capture());
        assertThat(input.getValue().pickCount()).isEqualTo(2);
    }

    @Test
    void 구성원이_아니면_검색도_AI도_부르지_않는다() {
        Mockito.when(tripService.findMemberTrip(99L, TRIP))
                .thenThrow(new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        assertThatThrownBy(() -> service.create(TRIP, 99L, command()))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);

        Mockito.verifyNoInteractions(placeMatcher);
        Mockito.verifyNoInteractions(llm);
    }

    // ---- given ----

    private void givenQueries(String... queries) {
        Mockito.when(llm.execute(Mockito.any(PollSearchTask.class), Mockito.any(PollSearchInput.class)))
                .thenReturn(new LlmResult<>(new PollSearchOutput(List.of(queries), "2일차에 어디 갈까요?"), 1));
    }

    private void givenFound(String query, PlaceMatcher.Candidate... places) {
        Mockito.when(placeMatcher.searchNear(Mockito.eq(query), Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyInt())).thenReturn(List.of(places));
    }

    private void givenPicks(int... numbers) {
        List<PollPickOutput.Pick> picks = new java.util.ArrayList<>();
        for (int number : numbers) {
            picks.add(new PollPickOutput.Pick(number, "관광지"));
        }
        Mockito.when(llm.execute(Mockito.any(PollPickTask.class), Mockito.any(PollPickInput.class)))
                .thenReturn(new LlmResult<>(new PollPickOutput(picks), 1));
    }

    private void givenItinerary(String... placeIds) {
        List<VisitDraft> visits = new java.util.ArrayList<>();
        for (int i = 0; i < placeIds.length; i++) {
            visits.add(VisitDraft.of("v-" + i, placeIds[i]));
        }
        ItineraryDraft draft = ItineraryDraft.of(new DayDraft(DAY1, visits));
        ItineraryVersion version = Mockito.mock(ItineraryVersion.class);
        Mockito.when(version.getContent()).thenReturn(JsonMapper.builder().build().writeValueAsString(draft));
        Mockito.when(versions.findFirstByTripIdOrderByVersionDesc(TRIP)).thenReturn(Optional.of(version));
    }

    private static PlaceMatcher.Candidate place(String placeId, String name) {
        return new PlaceMatcher.Candidate(placeId, name, "오사카시", 34.6, 135.5);
    }

    private static PollCandidateCommand command() {
        return new PollCandidateCommand("비용이 적은 대체 장소 찾기",
                List.of(new PollCandidateCommand.Message(21L, "유니버설 가고 싶어"),
                        new PollCandidateCommand.Message(32L, "입장료가 부담돼")));
    }
}
