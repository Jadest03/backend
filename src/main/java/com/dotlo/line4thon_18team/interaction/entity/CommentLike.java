package com.dotlo.line4thon_18team.interaction.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 댓글(답글 포함) 좋아요. 한 사람이 한 댓글에 한 번. */
@Entity
@Table(name = "item_comment_likes", uniqueConstraints = @UniqueConstraint(
        name = "uk_item_comment_likes_comment_user", columnNames = {"comment_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentLike extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private ItemComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static CommentLike of(ItemComment comment, User user) {
        CommentLike like = new CommentLike();
        like.comment = comment;
        like.user = user;
        return like;
    }
}
