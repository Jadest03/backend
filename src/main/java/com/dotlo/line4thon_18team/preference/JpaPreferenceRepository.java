package com.dotlo.line4thon_18team.preference;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 취향 DB 저장소. 같은 (tripId, userId)가 있으면 그 줄을 고친다. */
@Transactional
public class JpaPreferenceRepository implements PreferenceRepository {

    private final PreferenceEntityRepository entities;

    public JpaPreferenceRepository(PreferenceEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public StoredPreference save(StoredPreference preference) {
        PreferenceEntity entity = entities.findByTripIdAndUserId(preference.tripId(), preference.userId())
                .orElseGet(() -> new PreferenceEntity(preference.tripId(), preference.userId()));
        entity.update(preference.profile(), preference.rawText(), preference.updatedAt());
        return entities.save(entity).toStored();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredPreference> findByTripAndUser(long tripId, long userId) {
        return entities.findByTripIdAndUserId(tripId, userId).map(PreferenceEntity::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredPreference> findAllByTrip(long tripId) {
        return entities.findAllByTripIdOrderByUserIdAsc(tripId).stream()
                .map(PreferenceEntity::toStored)
                .toList();
    }

    @Override
    public void deleteByTrip(long tripId) {
        entities.deleteByTripId(tripId);
    }

    @Override
    public void deleteByUser(long userId) {
        entities.deleteByUserId(userId);
    }
}
