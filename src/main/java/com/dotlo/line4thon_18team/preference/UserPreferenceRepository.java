package com.dotlo.line4thon_18team.preference;

import java.util.Optional;

/**
 * 사용자 기본 취향 저장소. 서버에서는 JpaUserPreferenceRepository(DB),
 * 단위 테스트에서는 InMemoryUserPreferenceRepository를 쓴다.
 */
public interface UserPreferenceRepository {

    /** 같은 사용자의 값이 있으면 덮어쓴다. */
    StoredUserPreference save(StoredUserPreference preference);

    Optional<StoredUserPreference> findByUser(long userId);

    /** 회원 탈퇴용. 여행별 취향은 {@link PreferenceRepository#deleteByUser} 쪽이다. */
    void deleteByUser(long userId);
}
