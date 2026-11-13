package com.dotlo.line4thon_18team.share.repository;

import com.dotlo.line4thon_18team.share.entity.TripShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripShareRepository extends JpaRepository<TripShare, Long> {

    Optional<TripShare> findByTripId(Long tripId);

    Optional<TripShare> findByToken(String token);

    void deleteByTripId(Long tripId);
}
