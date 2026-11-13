package com.dotlo.line4thon_18team.chat;

import com.dotlo.line4thon_18team.chat.ai.CoordinationInput;
import com.dotlo.line4thon_18team.chat.ai.CoordinationOutput;
import com.dotlo.line4thon_18team.chat.ai.CoordinationTask;
import com.dotlo.line4thon_18team.chat.dto.CoordinationCommand;
import com.dotlo.line4thon_18team.chat.dto.CoordinationResponse;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.global.llm.LlmResult;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import com.dotlo.line4thon_18team.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 의견 조율 조립 (COLLAB-COORD-001): 사람을 번호로 바꿔 AI 에 넘기고, 돌아온 번호를 사용자 id 로 되돌린다.
 * AI 가 무엇을 어떻게 정리하는지는 {@link com.dotlo.line4thon_18team.chat.ai.CoordinationTaskTest}에서 본다.
 */
class ChatCoordinationServiceTest {

    private static final long TRIP = 1L;
    private static final long ME = 7L;
    private static final long DAEUN = 21L;
    private static final long JIWOO = 32L;
    private static final long STRANGER = 99L;

    private final TripService tripService = Mockito.mock(TripService.class);
    private final StructuredLlmExecutor llm = Mockito.mock(StructuredLlmExecutor.class);
    private final CoordinationTask task = new CoordinationTask(new SchemaLoader());

    private final ChatCoordinationService service = new ChatCoordinationService(tripService, llm, task);

    private Trip trip;

    @BeforeEach
    void setUp() {
        User user = User.builder().googleSub("sub").email("a@b.com").name("주인").build();
        trip = Trip.create(user, "오사카", LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 17),
                Companion.FRIEND, null);
        Mockito.when(tripService.findMemberTrip(ME, TRIP)).thenReturn(trip);
        Mockito.when(tripService.isMember(trip, DAEUN)).thenReturn(true);
        Mockito.when(tripService.isMember(trip, JIWOO)).thenReturn(true);
        Mockito.when(tripService.isMember(trip, STRANGER)).thenReturn(false);
    }

    @Test
    void 처음_말한_순서대로_번호를_붙여_넘긴다() {
        givenAnswer(new CoordinationOutput(List.of(), List.of("a", "b"), "정리했어요."));

        service.coordinate(TRIP, ME, List.of(
                message(JIWOO, "입장료가 부담돼"),
                message(DAEUN, "유니버설 가고 싶어"),
                message(JIWOO, "다른 곳도 보고 싶어")));

        ArgumentCaptor<CoordinationInput> input = ArgumentCaptor.forClass(CoordinationInput.class);
        Mockito.verify(llm).execute(Mockito.any(CoordinationTask.class), input.capture());
        assertThat(input.getValue().lines()).extracting(CoordinationInput.Line::memberNumber)
                .containsExactly(1, 2, 1);   // 지우가 먼저 말해서 1번
        assertThat(input.getValue().city()).isEqualTo("오사카");
    }

    @Test
    void 번호를_사용자_id로_되돌린다() {
        givenAnswer(new CoordinationOutput(
                List.of(new CoordinationOutput.Summary(1, "유니버설 스튜디오에 가고 싶어요"),
                        new CoordinationOutput.Summary(2, "비용 부담이 적은 곳을 원해요")),
                List.of("비용이 적은 대체 장소 찾기", "반나절만 머물고 다른 곳도 가기"),
                "두 분의 의견을 정리했어요."));

        CoordinationResponse response = service.coordinate(TRIP, ME, List.of(
                message(DAEUN, "유니버설 가고 싶어"), message(JIWOO, "입장료가 부담돼")));

        assertThat(response.text()).isEqualTo("두 분의 의견을 정리했어요.");
        assertThat(response.card().kind()).isEqualTo(CoordinationResponse.OPINION_SUMMARY);
        assertThat(response.card().summaries())
                .extracting(CoordinationResponse.Summary::userId, CoordinationResponse.Summary::point)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(DAEUN, "유니버설 스튜디오에 가고 싶어요"),
                        org.assertj.core.groups.Tuple.tuple(JIWOO, "비용 부담이 적은 곳을 원해요"));
    }

    @Test
    void 조금_더_이야기해_볼게요를_마지막에_붙인다() {
        givenAnswer(new CoordinationOutput(List.of(),
                List.of("비용이 적은 대체 장소 찾기", "반나절만 머물고 다른 곳도 가기"), "정리했어요."));

        CoordinationResponse response = service.coordinate(TRIP, ME, List.of(message(DAEUN, "유니버설 가고 싶어")));

        assertThat(response.options().title()).isEqualTo("어떤 방향으로 조율해 볼까요?");
        assertThat(response.options().items()).extracting(CoordinationResponse.Item::label)
                .containsExactly("비용이 적은 대체 장소 찾기", "반나절만 머물고 다른 곳도 가기", "조금 더 이야기해 볼게요");
        assertThat(response.options().items()).extracting(CoordinationResponse.Item::id)
                .containsExactly("d1", "d2", "talk-more");
    }

    @Test
    void 나간_사람의_메시지는_건너뛴다() {
        // 여행을 나갔거나 탈퇴해도 지난 메시지는 채팅방에 남는다. 그 사람 의견까지 조율에 넣지 않는다
        givenAnswer(new CoordinationOutput(List.of(), List.of("a", "b"), "정리했어요."));

        service.coordinate(TRIP, ME, List.of(
                message(DAEUN, "유니버설 가고 싶어"),
                message(STRANGER, "나 빠질게"),
                message(JIWOO, "입장료가 부담돼")));

        ArgumentCaptor<CoordinationInput> input = ArgumentCaptor.forClass(CoordinationInput.class);
        Mockito.verify(llm).execute(Mockito.any(CoordinationTask.class), input.capture());
        assertThat(input.getValue().lines()).extracting(CoordinationInput.Line::text)
                .containsExactly("유니버설 가고 싶어", "입장료가 부담돼");
        assertThat(input.getValue().lines()).extracting(CoordinationInput.Line::memberNumber)
                .containsExactly(1, 2);
    }

    @Test
    void 남은_구성원의_말이_하나도_없으면_400이고_AI를_부르지_않는다() {
        assertThatThrownBy(() -> service.coordinate(TRIP, ME, List.of(message(STRANGER, "나 빠질게"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_MESSAGES_TO_COORDINATE);

        Mockito.verifyNoInteractions(llm);
    }

    @Test
    void 구성원이_아니면_여행을_찾는_단계에서_막힌다() {
        Mockito.when(tripService.findMemberTrip(STRANGER, TRIP))
                .thenThrow(new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        assertThatThrownBy(() -> service.coordinate(TRIP, STRANGER, List.of(message(DAEUN, "가고 싶어"))))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);

        Mockito.verifyNoInteractions(llm);
    }

    // ---- helpers ----

    private void givenAnswer(CoordinationOutput output) {
        Mockito.when(llm.execute(Mockito.any(CoordinationTask.class), Mockito.any(CoordinationInput.class)))
                .thenReturn(new LlmResult<>(output, 1));
    }

    private static CoordinationCommand.Message message(long userId, String text) {
        return new CoordinationCommand.Message(userId, text);
    }
}
