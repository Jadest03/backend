package com.dotlo.line4thon_18team.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @NotBlank(message = "text는 필수입니다.")
        @Size(max = 500, message = "메시지는 500자 이하입니다.")
        String text
) {
}
