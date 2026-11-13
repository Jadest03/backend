package com.dotlo.line4thon_18team.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param parentId 답글이면 단 댓글 id. 답글에 단 답글은 원 댓글 아래로 붙는다 */
public record CommentCreateRequest(
        @NotBlank(message = "content는 필수입니다.")
        @Size(max = 500, message = "댓글은 500자 이하입니다.")
        String content,
        Long parentId
) {
}
