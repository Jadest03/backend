package com.dotlo.line4thon_18team.chat.dto;

import com.dotlo.line4thon_18team.chat.entity.ChatMessage;
import com.dotlo.line4thon_18team.chat.entity.ChatMessageType;
import com.dotlo.line4thon_18team.user.entity.User;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;

/**
 * @param sender  보낸 사람. AI 메시지는 null
 * @param payload 종류별 추가 데이터(JSON 그대로). 없으면 null
 */
public record ChatMessageResponse(
        Long id,
        ChatMessageType type,
        Sender sender,
        String text,
        @JsonRawValue String payload,
        LocalDateTime createdAt
) {

    /** @param characterId 캐릭터 번호(1~10). 탈퇴했거나 가입 전이면 null */
    public record Sender(Long id, String nickname, Integer characterId) {
    }

    public static ChatMessageResponse from(ChatMessage message) {
        User user = message.getSender();
        Sender sender = user == null ? null : new Sender(user.getId(), user.getDisplayName(), user.getCharacterId());
        return new ChatMessageResponse(message.getId(), message.getType(), sender, message.getText(),
                message.getPayload(), message.getCreatedAt());
    }
}
