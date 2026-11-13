package com.dotlo.line4thon_18team.interaction.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.interaction.dto.CommentCreateRequest;
import com.dotlo.line4thon_18team.interaction.dto.CommentResponse;
import com.dotlo.line4thon_18team.interaction.dto.InteractionListResponse;
import com.dotlo.line4thon_18team.interaction.dto.LikeResult;
import com.dotlo.line4thon_18team.interaction.service.ItemInteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 일정 항목(방문 장소) 좋아요·댓글. itemId 는 일정의 visits[].itemId */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ItemInteractionController {

    private final ItemInteractionService service;

    // 장소 좋아요 (여러 번 눌러도 한 번). 개수와 내 좋아요 여부를 돌려준다
    @PostMapping("/trips/{tripId}/items/{itemId}/likes")
    public ResponseEntity<LikeResult> likeItem(@LoginUser Long userId, @PathVariable Long tripId,
                                               @PathVariable String itemId) {
        return ResponseEntity.ok(service.likeItem(userId, tripId, itemId));
    }

    @DeleteMapping("/trips/{tripId}/items/{itemId}/likes")
    public ResponseEntity<LikeResult> unlikeItem(@LoginUser Long userId, @PathVariable Long tripId,
                                                 @PathVariable String itemId) {
        return ResponseEntity.ok(service.unlikeItem(userId, tripId, itemId));
    }

    // 댓글·답글 쓰기 (body: {"content": "...", "parentId": 답글이면 댓글 id})
    @PostMapping("/trips/{tripId}/items/{itemId}/comments")
    public ResponseEntity<CommentResponse> addComment(@LoginUser Long userId, @PathVariable Long tripId,
                                                      @PathVariable String itemId,
                                                      @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.ok(service.addComment(userId, tripId, itemId, request.content(), request.parentId()));
    }

    // 여행의 좋아요·댓글 전체: 장소 카드용 개수 + 댓글·답글 목록
    @GetMapping("/trips/{tripId}/comments")
    public ResponseEntity<InteractionListResponse> list(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(service.list(userId, tripId));
    }

    // 내 댓글 삭제 (답글도 같이) → 204
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@LoginUser Long userId, @PathVariable Long commentId) {
        service.deleteComment(userId, commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/likes")
    public ResponseEntity<LikeResult> likeComment(@LoginUser Long userId, @PathVariable Long commentId) {
        return ResponseEntity.ok(service.likeComment(userId, commentId));
    }

    @DeleteMapping("/comments/{commentId}/likes")
    public ResponseEntity<LikeResult> unlikeComment(@LoginUser Long userId, @PathVariable Long commentId) {
        return ResponseEntity.ok(service.unlikeComment(userId, commentId));
    }
}
