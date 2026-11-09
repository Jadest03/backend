package com.dotlo.line4thon_18team.interaction.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "item_comments", indexes = @Index(
        name = "idx_item_comments_trip_item_created", columnList = "trip_id,item_id,created_at,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemComment extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "item_id", nullable = false, columnDefinition = "varchar(100) collate utf8mb4_bin")
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    public static ItemComment of(Trip trip, String itemId, User user, String content) {
        ItemComment comment = new ItemComment();
        comment.trip = trip;
        comment.itemId = itemId;
        comment.user = user;
        comment.content = content;
        return comment;
    }
}
