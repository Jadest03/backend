package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.global.llm.FakeLlmClient;
import com.dotlo.line4thon_18team.global.llm.LlmCallException;
import com.dotlo.line4thon_18team.global.llm.LlmResponse;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.preference.ai.PreferenceInterpretationTask;
import com.dotlo.line4thon_18team.preference.domain.ChipType;
import com.dotlo.line4thon_18team.preference.domain.DayStartPreference;
import com.dotlo.line4thon_18team.preference.domain.PreferenceChip;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.preference.domain.TravelPace;
import com.dotlo.line4thon_18team.preference.domain.WalkingPreference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferenceServiceTest {

    private static final long TRIP = 10L;
    private static final long USER = 7L;
    private static final Instant NOW = Instant.parse("2026-11-02T01:00:00Z");

    private final FakeLlmClient llm = new FakeLlmClient();
    private final UserPreferenceService userPreferences = new UserPreferenceService(
            new InMemoryUserPreferenceRepository(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final PreferenceService service = new PreferenceService(
            new InMemoryPreferenceRepository(),
            userPreferences,
            new StructuredLlmExecutor(llm, 3, 0, millis -> { }),
            new PreferenceInterpretationTask(new SchemaLoader()),
            Clock.fixed(NOW, ZoneOffset.UTC));

    // ---- 텍스트 취향 AI 해석 ----

    @Test
    void 빈_입력이면_LLM을_부르지_않고_칩만으로_진행한다() {
        InterpretationResult result = service.interpret(TRIP, USER, "   ");

        assertThat(result.status()).isEqualTo(InterpretationStatus.EMPTY_INPUT);
        assertThat(result.preferences()).isEqualTo(PreferenceProfile.defaults());
        assertThat(result.chips()).isEmpty();
        assertThat(llm.callCount()).isZero();
    }

    @Test
    void 텍스트를_칩으로_해석하고_언급한_항목만_칩으로_보여준다() {
        llm.thenReturn("""
                {"pace": "RELAXED", "walking": "LESS", "dayStart": "UNSPECIFIED",
                 "interests": ["CAFE", "FOOD", "CAFE"], "avoid": ["SHOPPING"],
                 "notes": [" 해산물은 빼 주세요 "]}
                """);

        InterpretationResult result = service.interpret(TRIP, USER, "여유롭게, 덜 걷고 카페랑 맛집 위주로. 쇼핑은 빼고 해산물은 빼 주세요");

        assertThat(result.status()).isEqualTo(InterpretationStatus.INTERPRETED);
        PreferenceProfile profile = result.preferences();
        assertThat(profile.pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(profile.walking()).isEqualTo(WalkingPreference.LESS);
        assertThat(profile.dayStart()).isEqualTo(DayStartPreference.NORMAL);
        assertThat(profile.interests()).containsExactly(PlaceCategory.CAFE, PlaceCategory.FOOD);
        assertThat(profile.avoid()).containsExactly(PlaceCategory.SHOPPING);
        assertThat(profile.notes()).containsExactly("해산물은 빼 주세요");
        assertThat(result.chips()).extracting(PreferenceChip::type).containsExactly(
                ChipType.PACE, ChipType.WALKING, ChipType.INTEREST, ChipType.INTEREST, ChipType.AVOID, ChipType.NOTE);
        assertThat(result.chips().get(0).label()).isEqualTo("여유롭게");
        assertThat(result.chips().get(4).label()).isEqualTo("쇼핑 빼기");
        assertThat(result.fallbackReason()).isNull();
    }

    @Test
    void 호출이_안_되면_기본값으로_진행한다() {
        llm.thenThrow(new LlmCallException("preference-interpretation", "API 키 없음", false, null, null));

        InterpretationResult result = service.interpret(TRIP, USER, "카페 좋아요");

        assertThat(result.status()).isEqualTo(InterpretationStatus.FALLBACK_DEFAULT);
        assertThat(result.fallbackReason()).isEqualTo(FallbackReason.LLM_UNAVAILABLE);
        assertThat(result.preferences().pace()).isEqualTo(TravelPace.NORMAL);
    }

    @Test
    void 세_번_모두_규칙을_어기면_기본값으로_진행한다() {
        String contradictory = """
                {"pace": "NORMAL", "walking": "NORMAL", "dayStart": "NORMAL",
                 "interests": ["CAFE"], "avoid": ["CAFE"], "notes": []}
                """;
        llm.thenReturn(contradictory).thenReturn(contradictory).thenReturn(contradictory);

        InterpretationResult result = service.interpret(TRIP, USER, "카페 좋아하는데 카페는 빼줘");

        assertThat(result.status()).isEqualTo(InterpretationStatus.FALLBACK_DEFAULT);
        assertThat(result.fallbackReason()).isEqualTo(FallbackReason.LLM_OUTPUT_INVALID);
        assertThat(llm.callCount()).isEqualTo(3);
    }

    @Test
    void 모델이_거부하면_기본값으로_진행한다() {
        llm.thenRespond(new LlmResponse(null, "거부", "stop"));

        InterpretationResult result = service.interpret(TRIP, USER, "카페 좋아요");

        assertThat(result.fallbackReason()).isEqualTo(FallbackReason.LLM_REFUSED);
    }

    @Test
    void 입력이_500자를_넘으면_거절한다() {
        assertThatThrownBy(() -> service.interpret(TRIP, USER, "가".repeat(501)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PREFERENCE_TEXT_TOO_LONG);
                    assertThat(e.getMessage()).contains("500자").contains("현재 501자");
                });
        assertThat(llm.callCount()).isZero();
    }

    // ---- 취향 칩 저장·조회 ----

    @Test
    void 저장_전_조회는_기본값과_saved_false() {
        PreferenceView view = service.get(TRIP, USER);

        assertThat(view.saved()).isFalse();
        assertThat(view.preferences()).isEqualTo(PreferenceProfile.defaults());
        assertThat(view.chips()).extracting(PreferenceChip::label)
                .containsExactly("보통 강도", "보통 걷기", "보통 시간에 시작");
    }

    @Test
    void 저장하면_사용자별로_따로_조회된다() {
        service.save(TRIP, USER, input(TravelPace.PACKED, List.of(PlaceCategory.FOOD), List.of(), List.of(), "많이 보고 싶어요"));
        service.save(TRIP, 8L, input(TravelPace.RELAXED, List.of(), List.of(), List.of(), null));

        PreferenceView mine = service.get(TRIP, USER);
        assertThat(mine.saved()).isTrue();
        assertThat(mine.preferences().pace()).isEqualTo(TravelPace.PACKED);
        assertThat(mine.rawText()).isEqualTo("많이 보고 싶어요");
        assertThat(mine.updatedAt()).isEqualTo(NOW);
        assertThat(service.get(TRIP, 8L).preferences().pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(service.findAllByTrip(TRIP)).extracting(StoredPreference::userId).containsExactly(USER, 8L);
        assertThat(service.profileOrDefault(99L, USER)).isEqualTo(PreferenceProfile.defaults());
    }

    @Test
    void 다시_저장하면_덮어쓴다() {
        service.save(TRIP, USER, input(TravelPace.PACKED, List.of(), List.of(), List.of(), null));
        service.save(TRIP, USER, input(TravelPace.RELAXED, List.of(), List.of(), List.of(), null));

        assertThat(service.get(TRIP, USER).preferences().pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(service.findAllByTrip(TRIP)).hasSize(1);
    }

    @Test
    void 저장할_때_중복과_공백_메모를_정리한다() {
        PreferenceView view = service.save(TRIP, USER, input(TravelPace.NORMAL,
                List.of(PlaceCategory.CAFE, PlaceCategory.CAFE), List.of(), List.of(" 유모차 ", "", "유모차"), "  "));

        assertThat(view.preferences().interests()).containsExactly(PlaceCategory.CAFE);
        assertThat(view.preferences().notes()).containsExactly("유모차");
        assertThat(view.rawText()).isNull();
    }

    @Test
    void 규칙에_맞지_않는_값은_위반_목록과_함께_거절한다() {
        PreferenceInput invalid = new PreferenceInput(null, WalkingPreference.NORMAL, DayStartPreference.NORMAL,
                List.of(PlaceCategory.CAFE, PlaceCategory.OTHER), List.of(PlaceCategory.CAFE),
                List.of("1", "2", "3", "4", "5", "6"), null);

        assertThatThrownBy(() -> service.save(TRIP, USER, invalid))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PREFERENCE);
                    // 공통 에러 응답에는 message만 있어서 위반 내용을 이어 붙여 보낸다
                    assertThat(e.getMessage())
                            .startsWith(ErrorCode.INVALID_PREFERENCE.getMessage())
                            .contains("pace는 필수")
                            .contains("OTHER")
                            .contains("동시에")
                            .contains("최대 5개");
                    assertThat(e.getMessage().split(" / ")).hasSize(4);
                });
    }

    @Test
    void 목록에_null이_있으면_거절한다() {
        PreferenceInput withNull = new PreferenceInput(TravelPace.NORMAL, WalkingPreference.NORMAL,
                DayStartPreference.NORMAL, Arrays.asList(PlaceCategory.CAFE, null), List.of(), null, null);

        assertThatThrownBy(() -> service.save(TRIP, USER, withNull))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PREFERENCE));
    }

    @Test
    void 원문이_500자를_넘으면_거절한다() {
        assertThatThrownBy(() -> service.save(TRIP, USER,
                input(TravelPace.NORMAL, List.of(), List.of(), List.of(), "가".repeat(501))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PREFERENCE_TEXT_TOO_LONG));
    }

    // ---- 일정 생성에서 쓸 취향 (여행 취향 → 사용자 기본 취향 → 기본값) ----

    @Test
    void 여행_취향이_없으면_사용자_기본_취향을_쓴다() {
        userPreferences.save(USER, input(TravelPace.RELAXED, List.of(PlaceCategory.CAFE), List.of(), List.of(), null));

        PreferenceProfile profile = service.profileOrDefault(TRIP, USER);

        assertThat(profile.pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(profile.interests()).containsExactly(PlaceCategory.CAFE);
    }

    @Test
    void 여행_취향이_있으면_그것이_사용자_기본_취향보다_앞선다() {
        userPreferences.save(USER, input(TravelPace.RELAXED, List.of(PlaceCategory.CAFE), List.of(), List.of(), null));
        service.save(TRIP, USER, input(TravelPace.PACKED, List.of(PlaceCategory.FOOD), List.of(), List.of(), null));

        PreferenceProfile profile = service.profileOrDefault(TRIP, USER);

        assertThat(profile.pace()).isEqualTo(TravelPace.PACKED);
        assertThat(profile.interests()).containsExactly(PlaceCategory.FOOD);
    }

    @Test
    void 사용자_기본_취향은_다른_여행에도_쓰인다() {
        userPreferences.save(USER, input(TravelPace.RELAXED, List.of(), List.of(), List.of(), null));

        assertThat(service.profileOrDefault(TRIP, USER).pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(service.profileOrDefault(99L, USER).pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(service.profileOrDefault(TRIP, 8L)).isEqualTo(PreferenceProfile.defaults());
    }

    private static PreferenceInput input(TravelPace pace, List<PlaceCategory> interests, List<PlaceCategory> avoid,
                                         List<String> notes, String rawText) {
        return new PreferenceInput(pace, WalkingPreference.NORMAL, DayStartPreference.NORMAL,
                interests, avoid, notes, rawText);
    }
}
