package com.dotlo.line4thon_18team.itinerary.version.repository;

import com.dotlo.line4thon_18team.itinerary.version.entity.ItineraryVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItineraryVersionRepository extends JpaRepository<ItineraryVersion, Long> {

    Optional<ItineraryVersion> findFirstByTripIdOrderByVersionDesc(Long tripId);
}
