package com.dotlo.line4thon_18team.preference;

import java.util.List;
import java.util.Optional;

/**
 * 취향 저장소. 서버에서는 JpaPreferenceRepository(DB), 단위 테스트에서는 InMemoryPreferenceRepository를 쓴다.
 */
public interface PreferenceRepository {

    /** 같은 (tripId, userId)가 있으면 덮어쓴다. */
    StoredPreference save(StoredPreference preference);

    Optional<StoredPreference> findByTripAndUser(long tripId, long userId);

    /** 여행 구성원 전체의 취향 (동행자 기능 P2에서 사용). userId 오름차순. */
    List<StoredPreference> findAllByTrip(long tripId);

    /** 여행 삭제용. 그 여행의 취향을 구성원 것까지 모두 지운다. */
    void deleteByTrip(long tripId);

    /**
     * 회원 탈퇴용. 그 사용자의 여행별 취향을 여행과 상관없이 모두 지운다.
     *
     * <p>탈퇴해도 같이 쓰던 여행은 남는데, notes 와 rawText 는 사용자가 직접 쓴 문장이라
     * 남의 여행에 남으면 안 된다. 사용자 기본 취향은 {@link UserPreferenceRepository#deleteByUser} 쪽이다.
     */
    void deleteByUser(long userId);
}
