package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.preference.domain.PreferenceChip;
import com.dotlo.line4thon_18team.preference.domain.PreferenceChips;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;

import java.time.Instant;
import java.util.List;

/**
 * 사용자 기본 취향 저장·조회 응답. 여행별 취향 응답({@link PreferenceView})에서 tripId 만 빠진 모양이다.
 *
 * @param saved 저장된 값이 있으면 true. false면 기본값(보통)을 돌려준 것이다
 */
public record UserPreferenceView(long userId,
                                 boolean saved,
                                 PreferenceProfile preferences,
                                 List<PreferenceChip> chips,
                                 String rawText,
                                 Instant updatedAt) {

    public UserPreferenceView {
        chips = chips == null ? List.of() : List.copyOf(chips);
    }

    static UserPreferenceView of(StoredUserPreference stored) {
        return new UserPreferenceView(stored.userId(), true, stored.profile(),
                PreferenceChips.all(stored.profile()), stored.rawText(), stored.updatedAt());
    }

    static UserPreferenceView defaults(long userId) {
        PreferenceProfile defaults = PreferenceProfile.defaults();
        return new UserPreferenceView(userId, false, defaults, PreferenceChips.all(defaults), null, null);
    }
}
