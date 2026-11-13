package com.dotlo.line4thon_18team.member.repository;

import com.dotlo.line4thon_18team.member.entity.TripInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TripInviteRepository extends JpaRepository<TripInvite, Long> {

    Optional<TripInvite> findByToken(String token);

    // 아직 만료되지 않은 링크 (있으면 다시 쓴다)
    Optional<TripInvite> findFirstByTripIdAndExpiresAtAfterOrderByExpiresAtDesc(Long tripId, LocalDateTime now);

    void deleteAllByTripId(Long tripId);
}
