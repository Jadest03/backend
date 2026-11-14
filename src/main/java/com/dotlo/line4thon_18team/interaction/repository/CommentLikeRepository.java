package com.dotlo.line4thon_18team.interaction.repository;

import com.dotlo.line4thon_18team.interaction.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);
    long countByCommentId(Long commentId);
    void deleteByCommentIdAndUserId(Long commentId, Long userId);
    List<CommentLike> findAllByCommentTripId(Long tripId);
    void deleteAllByCommentIdIn(Collection<Long> commentIds);
    void deleteAllByCommentTripId(Long tripId);
}
