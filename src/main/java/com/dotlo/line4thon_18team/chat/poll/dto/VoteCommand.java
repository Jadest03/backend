package com.dotlo.line4thon_18team.chat.poll.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 투표 요청 바디.
 *
 * @param candidateId 고른 후보. 이미 투표했으면 그 표를 이 후보로 바꾼다
 */
public record VoteCommand(
        @NotNull(message = "candidateId는 필수입니다.")
        Long candidateId
) {
}
