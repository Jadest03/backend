package com.dotlo.line4thon_18team.itinerary.version.repository;

import com.dotlo.line4thon_18team.itinerary.version.entity.ItineraryVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItineraryVersionRepository extends JpaRepository<ItineraryVersion, Long> {

    Optional<ItineraryVersion> findFirstByTripIdOrderByVersionDesc(Long tripId);

    /** 변경 이력. 최신 버전부터. */
    List<ItineraryVersion> findByTripIdOrderByVersionDesc(Long tripId);

    /** 되돌릴 버전 하나. */
    Optional<ItineraryVersion> findByTripIdAndVersion(Long tripId, int version);

    /** 여행 삭제용. 벌크 delete 를 쓰지 않는 이유는 ItineraryTripCleanupService 주석에 있다. */
    void deleteByTripId(Long tripId);

    boolean existsByTripId(Long tripId);
}
