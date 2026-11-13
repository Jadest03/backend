package com.dotlo.line4thon_18team.member.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 여행 초대 링크. 만료 전이면 같은 링크를 다시 쓰고, 만료되면 새로 만든다.
 */
@Entity
@Table(name = "trip_invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripInvite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // 링크에 들어가는 값. 추측할 수 없게 랜덤으로 만든다
    @Column(nullable = false, unique = true, length = 32)
    private String token;

    // 초대 링크를 만든 사용자 id (미리보기의 "누가 초대했는지")
    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public static TripInvite create(Trip trip, String token, Long createdBy, LocalDateTime expiresAt) {
        TripInvite invite = new TripInvite();
        invite.trip = trip;
        invite.token = token;
        invite.createdBy = createdBy;
        invite.expiresAt = expiresAt;
        return invite;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
