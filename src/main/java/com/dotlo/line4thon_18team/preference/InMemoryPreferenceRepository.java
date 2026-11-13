package com.dotlo.line4thon_18team.preference;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 메모리 저장소. 단위 테스트용 (서버는 JpaPreferenceRepository를 쓴다). */
public class InMemoryPreferenceRepository implements PreferenceRepository {

    private record Key(long tripId, long userId) {
    }

    private final ConcurrentMap<Key, StoredPreference> store = new ConcurrentHashMap<>();

    @Override
    public StoredPreference save(StoredPreference preference) {
        store.put(new Key(preference.tripId(), preference.userId()), preference);
        return preference;
    }

    @Override
    public Optional<StoredPreference> findByTripAndUser(long tripId, long userId) {
        return Optional.ofNullable(store.get(new Key(tripId, userId)));
    }

    @Override
    public List<StoredPreference> findAllByTrip(long tripId) {
        return store.values().stream()
                .filter(preference -> preference.tripId() == tripId)
                .sorted(Comparator.comparingLong(StoredPreference::userId))
                .toList();
    }

    @Override
    public void deleteByTrip(long tripId) {
        store.keySet().removeIf(key -> key.tripId() == tripId);
    }

    @Override
    public void deleteByUser(long userId) {
        store.keySet().removeIf(key -> key.userId() == userId);
    }
}
