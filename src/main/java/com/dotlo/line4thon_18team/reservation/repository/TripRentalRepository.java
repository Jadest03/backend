package com.dotlo.line4thon_18team.reservation.repository;

import com.dotlo.line4thon_18team.reservation.entity.TripRental;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripRentalRepository extends JpaRepository<TripRental, Long> {

    @EntityGraph(attributePaths = "dates")
    Optional<TripRental> findByTripId(Long tripId);

    void deleteByTripId(Long tripId);
}
