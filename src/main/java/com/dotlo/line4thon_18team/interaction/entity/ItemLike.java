package com.dotlo.line4thon_18team.interaction.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 일정 버전이 바뀌어도 여행·항목·사용자 조합으로 유지한다. */
@Entity
@Table(name = "item_likes", uniqueConstraints = @UniqueConstraint(
        name = "uk_item_likes_trip_item_user", columnNames = {"trip_id", "item_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemLike extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // 외부 장소 ID가 아니라 일정 항목 ID. 항목이 빠져도 이 행은 보존한다.
    @Column(name = "item_id", nullable = false, columnDefinition = "varchar(100) collate utf8mb4_bin")
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static ItemLike of(Trip trip, String itemId, User user) {
        ItemLike like = new ItemLike();
        like.trip = trip;
        like.itemId = itemId;
        like.user = user;
        return like;
    }
}
