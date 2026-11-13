package com.dotlo.line4thon_18team.preference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PreferenceEntityRepository extends JpaRepository<PreferenceEntity, Long> {

    Optional<PreferenceEntity> findByTripIdAndUserId(Long tripId, Long userId);

    void deleteByTripId(Long tripId);

    void deleteByUserId(Long userId);

    List<PreferenceEntity> findAllByTripIdOrderByUserIdAsc(Long tripId);
}
