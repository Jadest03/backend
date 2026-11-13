package com.dotlo.line4thon_18team.chat.entity;

/**
 * 채팅 메시지 종류.
 * USER: 사람이 쓴 말 (전송 API 로만 들어온다). SYSTEM: "○○님이 참여했어요" 같은 알림. AI: 도틀로 답변 (카드·선택지는 payload).
 * SYSTEM 과 AI 는 보낸 사람이 없다.
 */
public enum ChatMessageType {
    USER, SYSTEM, AI
}
