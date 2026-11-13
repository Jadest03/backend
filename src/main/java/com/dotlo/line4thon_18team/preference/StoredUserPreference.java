package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;

import java.time.Instant;
import java.util.Objects;

/**
 * 저장된 사용자 기본 취향. 가입할 때 한 번 고르고, 여행별 취향을 입력하지 않았을 때 쓴다.
 * 여행에 묶이지 않는다는 점만 {@link StoredPreference}와 다르다.
 *
 * @param rawText 사용자가 입력한 취향 원문 (선택, 최대 500자)
 */
public record StoredUserPreference(long userId, PreferenceProfile profile, String rawText, Instant updatedAt) {

    public StoredUserPreference {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
