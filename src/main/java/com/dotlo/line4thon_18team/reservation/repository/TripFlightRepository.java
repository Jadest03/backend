package com.dotlo.line4thon_18team.reservation.repository;

import com.dotlo.line4thon_18team.reservation.entity.TripFlight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripFlightRepository extends JpaRepository<TripFlight, Long> {

    List<TripFlight> findAllByTripId(Long tripId);

    void deleteAllByTripId(Long tripId);
}
