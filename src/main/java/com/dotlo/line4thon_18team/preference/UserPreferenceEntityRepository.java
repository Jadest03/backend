package com.dotlo.line4thon_18team.preference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferenceEntityRepository extends JpaRepository<UserPreferenceEntity, Long> {

    Optional<UserPreferenceEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
