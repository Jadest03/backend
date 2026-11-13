package com.dotlo.line4thon_18team.itinerary.edit.repository;

import com.dotlo.line4thon_18team.itinerary.edit.entity.ItineraryEditRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItineraryEditRequestRepository extends JpaRepository<ItineraryEditRequest, Long> {

    /** 그 버전을 고친 수정안 중 가장 나중 것. 같은 버전으로 여러 번 요청할 수 있어서 마지막 것을 쓴다. */
    Optional<ItineraryEditRequest> findFirstByTripIdAndBaseVersionOrderByIdDesc(Long tripId, int baseVersion);

    /** 여행 삭제용. */
    void deleteByTripId(Long tripId);
}
