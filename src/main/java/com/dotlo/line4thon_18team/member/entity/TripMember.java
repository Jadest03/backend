package com.dotlo.line4thon_18team.member.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 초대를 수락해서 여행에 참여한 사람. 여행을 만든 사람(방장)은 trips.user 라서 여기에 넣지 않는다.
 */
@Entity
@Table(
        name = "trip_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_trip_member_trip_user", columnNames = {"trip_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static TripMember create(Trip trip, User user) {
        TripMember member = new TripMember();
        member.trip = trip;
        member.user = user;
        return member;
    }
}
