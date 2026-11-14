package com.dotlo.line4thon_18team.chat.poll.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 투표 후보 만들기 요청 바디.
 *
 * @param direction 의견 조율에서 사용자가 고른 방향의 문구. 예: "비용이 적은 대체 장소 찾기"
 * @param messages  나눈 이야기. 의견 조율과 같은 모양을 받지만 여기서는 text 만 쓴다.
 *                  어떤 장소를 찾을지 정하는 데 누구 말인지는 영향을 주지 않고, 요약을 사람에게
 *                  되돌리지도 않아서다. 프론트가 같은 값을 그대로 보낼 수 있게 모양만 맞춰 둔다
 */
public record PollCandidateCommand(
        @NotBlank(message = "direction은 필수입니다.")
        @Size(max = 100, message = "방향은 100자 이하로 보내 주세요.")
        String direction,

        @Size(max = 100, message = "대화는 한 번에 100개까지 보낼 수 있습니다.")
        @Valid
        List<Message> messages
) {

    public record Message(
            Long userId,

            @NotBlank(message = "text는 필수입니다.")
            @Size(max = 500, message = "메시지는 500자 이하입니다.")
            String text
    ) {
    }
}
