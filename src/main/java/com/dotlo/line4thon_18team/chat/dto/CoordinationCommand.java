package com.dotlo.line4thon_18team.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 의견 조율 요청 바디.
 *
 * <p>채팅 메시지 테이블이 생기기 전까지는 프론트가 화면에 들고 있는 대화를 그대로 보낸다.
 * 테이블이 생기면 tripId 만 받아 서버가 읽도록 바꾸고, 응답 모양은 그대로 둔다.
 *
 * @param messages 오래된 것부터. 도틀로가 한 말은 빼고 사람이 쓴 것만 보낸다
 */
public record CoordinationCommand(
        @NotEmpty(message = "messages는 비어 있을 수 없습니다.")
        @Size(max = 100, message = "대화는 한 번에 100개까지 보낼 수 있습니다.")
        @Valid
        List<Message> messages
) {

    public record Message(
            @NotNull(message = "userId는 필수입니다.")
            Long userId,

            @NotBlank(message = "text는 필수입니다.")
            @Size(max = 500, message = "메시지는 500자 이하입니다.")
            String text
    ) {
    }
}
