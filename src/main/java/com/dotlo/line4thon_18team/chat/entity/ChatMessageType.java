package com.dotlo.line4thon_18team.chat.entity;

/**
 * 채팅 메시지 종류.
 * TEXT: 사람이 쓴 말. AI: 도틀로 답변 (보낸 사람 없음). POLL: 투표 카드 (payload 에 pollId).
 */
public enum ChatMessageType {
    TEXT, AI, POLL
}
