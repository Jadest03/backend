package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.preference.domain.PreferenceRules;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 사용자 기본 취향 (가입할 때 고르는 취향). 여행별 취향을 입력하지 않았을 때 쓴다.
 *
 * <p>값 검사는 여행별 취향과 똑같이 {@link PreferenceRules}로 한다. 요청 바디도 같은 모양이라
 * 화면에서 취향 선택 부분을 그대로 쓸 수 있다.
 */
public class UserPreferenceService {

    private final UserPreferenceRepository repository;
    private final Clock clock;

    public UserPreferenceService(UserPreferenceRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 저장. 기존 값은 덮어쓴다. */
    public UserPreferenceView save(long userId, PreferenceInput input) {
        if (input == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        }
        String rawText = input.rawText() == null ? null : input.rawText().strip();
        if (rawText != null && rawText.length() > PreferenceRules.MAX_RAW_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.PREFERENCE_TEXT_TOO_LONG);
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
        StoredUserPreference stored = repository.save(new StoredUserPreference(userId, profile,
                rawText == null || rawText.isEmpty() ? null : rawText, clock.instant()));
        return UserPreferenceView.of(stored);
    }

    /** 조회. 저장한 적이 없으면 기본값(saved=false)을 돌려준다. */
    public UserPreferenceView get(long userId) {
        return repository.findByUser(userId)
                .map(UserPreferenceView::of)
                .orElseGet(() -> UserPreferenceView.defaults(userId));
    }

    /** 여행별 취향이 없을 때 쓸 값. 가입할 때 고른 적이 없으면 비어 있다. */
    public Optional<PreferenceProfile> profile(long userId) {
        return repository.findByUser(userId).map(StoredUserPreference::profile);
    }
}
