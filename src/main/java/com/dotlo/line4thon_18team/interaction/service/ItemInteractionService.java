package com.dotlo.line4thon_18team.interaction.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.interaction.dto.CommentResponse;
import com.dotlo.line4thon_18team.interaction.dto.InteractionListResponse;
import com.dotlo.line4thon_18team.interaction.dto.InteractionListResponse.ItemSummary;
import com.dotlo.line4thon_18team.interaction.dto.LikeResult;
import com.dotlo.line4thon_18team.interaction.entity.CommentLike;
import com.dotlo.line4thon_18team.interaction.entity.ItemComment;
import com.dotlo.line4thon_18team.interaction.entity.ItemLike;
import com.dotlo.line4thon_18team.interaction.repository.CommentLikeRepository;
import com.dotlo.line4thon_18team.interaction.repository.ItemCommentRepository;
import com.dotlo.line4thon_18team.interaction.repository.ItemLikeRepository;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.trip.service.TripService;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일정 항목(방문 장소) 좋아요·댓글·답글, 댓글 좋아요. 여행 구성원만 쓸 수 있고 아니면 404.
 *
 * <p>항목은 일정 버전이 바뀌어도 유지되는 itemId 로 붙는다. 최신 일정에서 빠진 항목은 기록을 남기고
 * 목록에서만 숨기며, 그 항목에 새로 좋아요·댓글을 달면 ITEM_NOT_FOUND (취소·삭제는 된다). 공항·숙소 카드는 itemId 가 없어서 아직 안 된다.
 *
 * <p>쓰기는 여행 행을 잠근다. 같은 사람이 좋아요를 두 번 빠르게 눌러도 한 줄만 생긴다.
 * 잠근 뒤 다른 요청이 커밋한 행이 보이도록 READ_COMMITTED 로 연다 (나가기·초대 수락과 같은 이유).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemInteractionService {

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final ItineraryService itineraryService;
    private final ItemLikeRepository itemLikes;
    private final ItemCommentRepository comments;
    private final CommentLikeRepository commentLikes;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeResult likeItem(Long userId, Long tripId, String itemId) {
        Trip trip = lockMemberTrip(userId, tripId);
        requireActiveItem(tripId, itemId);
        if (!itemLikes.existsByTripIdAndItemIdAndUserId(tripId, itemId, userId)) {
            itemLikes.save(ItemLike.of(trip, itemId, userRepository.getReferenceById(userId)));
        }
        return new LikeResult(itemLikes.countByTripIdAndItemId(tripId, itemId), true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    // 취소는 일정에서 빠진 장소여도 된다 (내 좋아요를 거두는 건 막을 이유가 없다)
    public LikeResult unlikeItem(Long userId, Long tripId, String itemId) {
        lockMemberTrip(userId, tripId);
        itemLikes.deleteByTripIdAndItemIdAndUserId(tripId, itemId, userId);
        return new LikeResult(itemLikes.countByTripIdAndItemId(tripId, itemId), false);
    }

    /** parentId 가 있으면 답글. 답글에 단 답글은 원 댓글 아래로 붙인다 (한 단계만). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommentResponse addComment(Long userId, Long tripId, String itemId, String content, Long parentId) {
        Trip trip = lockMemberTrip(userId, tripId);
        requireActiveItem(tripId, itemId);
        Long rootId = null;
        if (parentId != null) {
            ItemComment parent = comments.findById(parentId)
                    .filter(c -> c.getTrip().getId().equals(tripId) && c.getItemId().equals(itemId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            rootId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
        }
        ItemComment saved = comments.save(ItemComment.of(trip, itemId, userRepository.getReferenceById(userId),
                content, rootId));
        return CommentResponse.of(saved, 0, false);
    }

    /** 쓴 사람만 지운다. 댓글을 지우면 달린 답글도 같이 지운다. 남의 댓글이면 COMMENT_NOT_FOUND. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteComment(Long userId, Long commentId) {
        ItemComment comment = findMemberComment(userId, commentId);
        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        List<ItemComment> targets = new ArrayList<>(comments.findAllByParentId(commentId));
        targets.add(comment);
        commentLikes.deleteAllByCommentIdIn(targets.stream().map(ItemComment::getId).toList());
        comments.deleteAll(targets);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeResult likeComment(Long userId, Long commentId) {
        ItemComment comment = findMemberComment(userId, commentId);
        if (!commentLikes.existsByCommentIdAndUserId(commentId, userId)) {
            commentLikes.save(CommentLike.of(comment, userRepository.getReferenceById(userId)));
        }
        return new LikeResult(commentLikes.countByCommentId(commentId), true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeResult unlikeComment(Long userId, Long commentId) {
        findMemberComment(userId, commentId);
        commentLikes.deleteByCommentIdAndUserId(commentId, userId);
        return new LikeResult(commentLikes.countByCommentId(commentId), false);
    }

    // ponytail: 여행의 좋아요를 전부 읽어 메모리에서 센다. 일행 몇 명·장소 수십 개라 충분하고, 커지면 group by 로 바꾼다
    public InteractionListResponse list(Long userId, Long tripId) {
        tripService.findMemberTrip(userId, tripId);
        Set<String> active = activeItemIds(tripId);
        if (active.isEmpty()) {
            return new InteractionListResponse(List.of(), List.of());
        }

        List<ItemLike> likes = itemLikes.findAllByTripId(tripId);
        List<ItemComment> itemComments = comments.findAllByTripIdAndItemIdInOrderByCreatedAtAscIdAsc(tripId, active);
        Map<String, Long> commentCounts = itemComments.stream()
                .collect(Collectors.groupingBy(ItemComment::getItemId, Collectors.counting()));
        List<ItemSummary> items = active.stream().map(itemId -> new ItemSummary(itemId,
                likes.stream().filter(l -> l.getItemId().equals(itemId)).count(),
                likes.stream().anyMatch(l -> l.getItemId().equals(itemId) && l.getUser().getId().equals(userId)),
                commentCounts.getOrDefault(itemId, 0L))).toList();

        Map<Long, List<CommentLike>> likesByComment = commentLikes.findAllByCommentTripId(tripId).stream()
                .collect(Collectors.groupingBy(l -> l.getComment().getId()));
        List<CommentResponse> commentResponses = itemComments.stream().map(c -> {
            List<CommentLike> cl = likesByComment.getOrDefault(c.getId(), List.of());
            return CommentResponse.of(c, cl.size(), cl.stream().anyMatch(l -> l.getUser().getId().equals(userId)));
        }).toList();
        return new InteractionListResponse(items, commentResponses);
    }

    /** 여행 삭제용. 권한 확인은 부르는 쪽이 한다. */
    @Transactional
    public void deleteByTrip(long tripId) {
        commentLikes.deleteAllByCommentTripId(tripId);
        comments.deleteAllByTripId(tripId);
        itemLikes.deleteAllByTripId(tripId);
    }

    private Trip lockMemberTrip(Long userId, Long tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!tripService.isMember(trip, userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    // 댓글이 없거나, 그 여행 구성원이 아니면 COMMENT_NOT_FOUND (남의 여행 댓글이 있는지 드러내지 않는다)
    private ItemComment findMemberComment(Long userId, Long commentId) {
        ItemComment comment = comments.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        Trip trip = tripRepository.findByIdForUpdate(comment.getTrip().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!tripService.isMember(trip, userId)) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private void requireActiveItem(Long tripId, String itemId) {
        if (!activeItemIds(tripId).contains(itemId)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
    }

    // 최신 일정의 방문 장소 itemId (일정 순서대로)
    private Set<String> activeItemIds(Long tripId) {
        return itineraryService.findLatest(tripId)
                .map(view -> view.draft().days().stream()
                        .flatMap(day -> day.visits().stream())
                        .map(VisitDraft::itemId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }
}
