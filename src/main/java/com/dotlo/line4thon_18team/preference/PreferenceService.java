package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.global.llm.LlmException;
import com.dotlo.line4thon_18team.global.llm.LlmOutputInvalidException;
import com.dotlo.line4thon_18team.global.llm.LlmRefusalException;
import com.dotlo.line4thon_18team.global.llm.LlmResult;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.preference.ai.PreferenceInterpretationOutput;
import com.dotlo.line4thon_18team.preference.ai.PreferenceInterpretationTask;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.preference.domain.PreferenceRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * 취향 API 3개의 로직.
 * <ul>
 *   <li>텍스트 취향 AI 해석 (저장 안 함)</li>
 *   <li>취향 칩 저장 (여행 + 사용자 단위)</li>
 *   <li>취향 조회 (없으면 기본값)</li>
 * </ul>
 */
public class PreferenceService {

    private static final Logger log = LoggerFactory.getLogger(PreferenceService.class);

    private final PreferenceRepository repository;
    private final UserPreferenceService userPreferences;
    private final StructuredLlmExecutor llm;
    private final PreferenceInterpretationTask interpretationTask;
    private final Clock clock;

    public PreferenceService(PreferenceRepository repository, UserPreferenceService userPreferences,
                             StructuredLlmExecutor llm, PreferenceInterpretationTask interpretationTask,
                             Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.userPreferences = Objects.requireNonNull(userPreferences, "userPreferences");
        this.llm = Objects.requireNonNull(llm, "llm");
        this.interpretationTask = Objects.requireNonNull(interpretationTask, "interpretationTask");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * PREF-TEXT-001. 텍스트 취향을 칩으로 해석한다. 저장은 하지 않는다.
     * 빈 입력이면 LLM을 부르지 않고, 해석에 실패하면 오류 대신 기본값을 돌려준다.
     */
    public InterpretationResult interpret(long tripId, long userId, String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.length() > PreferenceRules.MAX_RAW_TEXT_LENGTH) {
            throw textTooLong(trimmed.length());
        }
        if (trimmed.isEmpty()) {
            return InterpretationResult.emptyInput();
        }
        try {
            LlmResult<PreferenceInterpretationOutput> result = llm.execute(interpretationTask, trimmed);
            PreferenceInterpretationOutput output = result.value();
            return InterpretationResult.interpreted(
                    PreferenceInterpretationTask.toProfile(output),
                    PreferenceInterpretationTask.explicitSingles(output));
        } catch (LlmRefusalException e) {
            log.warn("취향 해석 거부 tripId={} userId={}: {}", tripId, userId, e.getMessage());
            return InterpretationResult.fallback(FallbackReason.LLM_REFUSED);
        } catch (LlmOutputInvalidException e) {
            log.warn("취향 해석 출력 오류 tripId={} userId={}: {}", tripId, userId, e.getAttemptErrors());
            return InterpretationResult.fallback(FallbackReason.LLM_OUTPUT_INVALID);
        } catch (LlmException e) {
            log.warn("취향 해석 호출 실패 tripId={} userId={}: {}", tripId, userId, e.getMessage());
            return InterpretationResult.fallback(FallbackReason.LLM_UNAVAILABLE);
        }
    }

    /** 취향 칩 저장. 같은 여행·사용자의 기존 값은 덮어쓴다. */
    public PreferenceView save(long tripId, long userId, PreferenceInput input) {
        if (input == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        }
        String rawText = input.rawText() == null ? null : input.rawText().strip();
        if (rawText != null && rawText.length() > PreferenceRules.MAX_RAW_TEXT_LENGTH) {
            throw textTooLong(rawText.length());
        }
        List<String> violations = PreferenceRules.violations(input.pace(), input.walking(), input.dayStart(),
                input.interests(), input.avoid(), input.notes());
        if (!violations.isEmpty()) {
            // 공통 에러 응답은 {status, code, message}라서 위반 내용을 message에 이어 붙인다
            throw new BusinessException(ErrorCode.INVALID_PREFERENCE,
                    ErrorCode.INVALID_PREFERENCE.getMessage() + " " + String.join(" / ", violations));
        }
        PreferenceProfile profile = new PreferenceProfile(input.pace(), input.walking(), input.dayStart(),
                PreferenceRules.distinct(input.interests()), PreferenceRules.distinct(input.avoid()),
                PreferenceRules.cleanNotes(input.notes()));
        StoredPreference stored = repository.save(new StoredPreference(tripId, userId, profile,
                rawText == null || rawText.isEmpty() ? null : rawText, clock.instant()));
        return PreferenceView.of(stored);
    }

    /** 취향 조회. 저장한 적이 없으면 기본값(saved=false)을 돌려준다. */
    public PreferenceView get(long tripId, long userId) {
        return repository.findByTripAndUser(tripId, userId)
                .map(PreferenceView::of)
                .orElseGet(() -> PreferenceView.defaults(tripId, userId));
    }

    /**
     * 일정 생성·수정에서 쓸 취향. 이 여행에 취향을 입력했으면 그것, 아니면 가입할 때 고른
     * 사용자 기본 취향, 그것도 없으면 기본값(보통)을 쓴다.
     */
    public PreferenceProfile profileOrDefault(long tripId, long userId) {
        return repository.findByTripAndUser(tripId, userId)
                .map(StoredPreference::profile)
                .or(() -> userPreferences.profile(userId))
                .orElseGet(PreferenceProfile::defaults);
    }

    /** 동행자 기능(P2)에서 구성원별 취향을 합칠 때 쓴다. */
    public List<StoredPreference> findAllByTrip(long tripId) {
        return repository.findAllByTrip(tripId);
    }

    private static BusinessException textTooLong(int length) {
        return new BusinessException(ErrorCode.PREFERENCE_TEXT_TOO_LONG,
                ErrorCode.PREFERENCE_TEXT_TOO_LONG.getMessage() + " (현재 " + length + "자)");
    }
}
