package com.dotlo.line4thon_18team.share.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행의 공유 링크. 여행마다 1개이고, 다시 요청하면 같은 토큰을 돌려준다.
 */
@Entity
@Table(name = "trip_share")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripShare extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    // 링크에 들어가는 값. 추측할 수 없게 랜덤으로 만든다
    @Column(nullable = false, unique = true, length = 32)
    private String token;

    public static TripShare create(Trip trip, String token) {
        TripShare share = new TripShare();
        share.trip = trip;
        share.token = token;
        return share;
    }
}
