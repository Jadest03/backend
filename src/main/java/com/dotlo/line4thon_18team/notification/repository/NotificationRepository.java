package com.dotlo.line4thon_18team.notification.repository;

import com.dotlo.line4thon_18team.notification.entity.Notification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = "trip")
    List<Notification> findTop50ByUserIdOrderByIdDesc(Long userId);

    boolean existsByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int readAll(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 여행에서 나갈 때: 그 여행에서 받은 내 알림
    void deleteAllByTripIdAndUserId(Long tripId, Long userId);

    // 여행 삭제용. 벌크 delete 를 쓰지 않는 이유는 ItineraryTripCleanupService 주석에 있다
    void deleteAllByTripId(Long tripId);
}
