package com.dotlo.line4thon_18team.interaction.dto;

import java.util.List;

/**
 * 여행의 좋아요·댓글. 지금 일정에 있는 방문 장소만 담는다.
 *
 * @param items    방문 장소마다 카드에 띄울 수 (♡, 💬). commentCount 는 답글 포함
 * @param comments 댓글과 답글을 오래된 것부터 한 줄로. 답글은 parentId 로 묶는다
 */
public record InteractionListResponse(List<ItemSummary> items, List<CommentResponse> comments) {

    /** @param liked 내가 이 장소에 좋아요를 눌렀는지 */
    public record ItemSummary(String itemId, long likeCount, boolean liked, long commentCount) {
    }
}
