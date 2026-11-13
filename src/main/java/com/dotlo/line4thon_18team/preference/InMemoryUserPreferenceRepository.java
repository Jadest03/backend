package com.dotlo.line4thon_18team.preference;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 메모리 저장소. 단위 테스트용 (서버는 JpaUserPreferenceRepository를 쓴다). */
public class InMemoryUserPreferenceRepository implements UserPreferenceRepository {

    private final ConcurrentMap<Long, StoredUserPreference> store = new ConcurrentHashMap<>();

    @Override
    public StoredUserPreference save(StoredUserPreference preference) {
        store.put(preference.userId(), preference);
        return preference;
    }

    @Override
    public Optional<StoredUserPreference> findByUser(long userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public void deleteByUser(long userId) {
        store.remove(userId);
    }
}
