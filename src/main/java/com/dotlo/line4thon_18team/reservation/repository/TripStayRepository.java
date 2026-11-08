package com.dotlo.line4thon_18team.reservation.repository;

import com.dotlo.line4thon_18team.reservation.entity.TripStay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripStayRepository extends JpaRepository<TripStay, Long> {

    List<TripStay> findAllByTripIdOrderByCheckInAsc(Long tripId);

    void deleteAllByTripId(Long tripId);
}
