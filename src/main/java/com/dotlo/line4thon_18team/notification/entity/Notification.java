package com.dotlo.line4thon_18team.notification.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 한 사람이 받은 알림 1개. 같은 일을 일행 여럿이 받으면 사람마다 한 줄씩 생긴다. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_user_id", columnList = "user_id,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 받는 사람
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private NotificationType type;

    // 화면에 그대로 보이는 문구
    @Column(nullable = false, length = 500)
    private String message;

    // 문구 안에서 굵게 보일 부분 (장소 이름 등). 없으면 null
    @Column(length = 100)
    private String highlight;

    private LocalDateTime readAt;

    public static Notification create(User user, Trip trip, NotificationType type, String message, String highlight) {
        Notification notification = new Notification();
        notification.user = user;
        notification.trip = trip;
        notification.type = type;
        notification.message = message;
        notification.highlight = highlight;
        return notification;
    }

    public void read(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
