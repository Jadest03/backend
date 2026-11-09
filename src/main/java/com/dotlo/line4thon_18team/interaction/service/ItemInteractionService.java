package com.dotlo.line4thon_18team.interaction.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.interaction.dto.CommentResult;
import com.dotlo.line4thon_18team.interaction.dto.LikeResult;
import com.dotlo.line4thon_18team.interaction.entity.ItemComment;
import com.dotlo.line4thon_18team.interaction.entity.ItemLike;
import com.dotlo.line4thon_18team.interaction.port.InteractionAccess;
import com.dotlo.line4thon_18team.interaction.repository.ItemCommentRepository;
import com.dotlo.line4thon_18team.interaction.repository.ItemLikeRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * BE2 내부 처리. 실제 InteractionAccess 구현과 함께 명시적으로 빈 등록 후 컨트롤러를 연결한다.
 * 임시로 여행 소유자만 허용하거나 모든 항목을 허용하는 기본 구현은 두지 않는다.
 */
@RequiredArgsConstructor
public class ItemInteractionService {
    @NonNull private final InteractionAccess access;
    @NonNull private final ItemLikeRepository likes;
    @NonNull private final ItemCommentRepository comments;
    @NonNull private final EntityManager em;
    @NonNull private final TransactionTemplate transactions;

    public LikeResult like(Long userId, Long tripId, String itemId) {
        validateTarget(userId, tripId, itemId);
        return transactions.execute(status -> {
            Trip trip = lockTrip(tripId);
            requireActiveItem(userId, tripId, itemId);
            if (!likes.existsByTripIdAndItemIdAndUserId(tripId, itemId, userId)) {
                likes.saveAndFlush(ItemLike.of(trip, itemId, user(userId)));
            }
            return new LikeResult(likes.countByTripIdAndItemId(tripId, itemId), true);
        });
    }

    public LikeResult unlike(Long userId, Long tripId, String itemId) {
        validateTarget(userId, tripId, itemId);
        return transactions.execute(status -> {
            lockTrip(tripId);
            requireActiveItem(userId, tripId, itemId);
            likes.deleteByTripIdAndItemIdAndUserId(tripId, itemId, userId);
            likes.flush();
            return new LikeResult(likes.countByTripIdAndItemId(tripId, itemId), false);
        });
    }

    public CommentResult addComment(Long userId, Long tripId, String itemId, String content) {
        validateTarget(userId, tripId, itemId);
        // 기획의 글자 수 제한은 미정. 현재는 빈 글과 MySQL TEXT의 바이트 용량 초과만 막는다.
        if (content == null || content.isBlank() || content.getBytes(StandardCharsets.UTF_8).length > 65_535) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return transactions.execute(status -> {
            Trip trip = lockTrip(tripId);
            requireActiveItem(userId, tripId, itemId);
            return CommentResult.from(comments.saveAndFlush(ItemComment.of(trip, itemId, user(userId), content)));
        });
    }

    // 명세의 댓글 목록은 여행 단위. 현재 일정에서 빠진 항목의 댓글은 조회에서만 제외한다.
    public List<CommentResult> listComments(Long userId, Long tripId) {
        validateIds(userId, tripId);
        return transactions.execute(status -> {
            var active = access.activeItemIds(userId, tripId);
            if (active.isEmpty()) return List.of();
            return comments.findAllByTripIdAndItemIdInOrderByCreatedAtAscIdAsc(tripId, active)
                    .stream().map(CommentResult::from).toList();
        });
    }

    // 명세의 삭제 주소는 commentId만 받으므로 작성자 기준으로 여행·항목을 찾아 검사한다.
    public void deleteComment(Long userId, Long commentId) {
        validateIds(userId, commentId);
        var target = comments.findByIdAndUserId(commentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        Long tripId = target.getTrip().getId();
        String itemId = target.getItemId();
        transactions.executeWithoutResult(status -> {
            lockTrip(tripId);
            requireActiveItem(userId, tripId, itemId);
            if (comments.deleteOwned(commentId, userId) == 0) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }
        });
    }

    private Trip lockTrip(Long tripId) {
        Trip trip = em.find(Trip.class, tripId, LockModeType.PESSIMISTIC_WRITE);
        if (trip == null) throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        return trip;
    }

    private User user(Long userId) {
        User user = em.find(User.class, userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        return user;
    }

    private void requireActiveItem(Long userId, Long tripId, String itemId) {
        if (!access.activeItemIds(userId, tripId).contains(itemId)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
    }

    private static void validateTarget(Long userId, Long tripId, String itemId) {
        validateIds(userId, tripId);
        if (itemId == null || itemId.isBlank() || itemId.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static void validateIds(Long first, Long second) {
        if (first == null || first < 1 || second == null || second < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
