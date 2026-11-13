package com.dotlo.line4thon_18team.interaction.dto;

import com.dotlo.line4thon_18team.interaction.entity.ItemComment;
import com.dotlo.line4thon_18team.user.entity.User;

import java.time.LocalDateTime;

/**
 * @param parentId 답글이면 원 댓글 id, 댓글이면 null
 * @param liked    내가 이 댓글에 좋아요를 눌렀는지
 */
public record CommentResponse(Long id, String itemId, Long parentId, String content, Author author,
                              long likeCount, boolean liked, LocalDateTime createdAt) {

    /** @param characterId 캐릭터 번호(1~10). 탈퇴했으면 null */
    public record Author(Long id, String nickname, Integer characterId) {
    }

    public static CommentResponse of(ItemComment comment, long likeCount, boolean liked) {
        User user = comment.getUser();
        return new CommentResponse(comment.getId(), comment.getItemId(), comment.getParentId(), comment.getContent(),
                new Author(user.getId(), user.getDisplayName(), user.getCharacterId()),
                likeCount, liked, comment.getCreatedAt());
    }
}
