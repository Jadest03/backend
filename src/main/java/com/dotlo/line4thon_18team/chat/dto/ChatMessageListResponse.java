package com.dotlo.line4thon_18team.chat.dto;

import java.util.List;

/** @param messages 오래된 것부터 */
public record ChatMessageListResponse(List<ChatMessageResponse> messages) {
}
