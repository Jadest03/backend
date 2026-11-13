package com.dotlo.line4thon_18team.notification.entity;

/**
 * 알림 종류. FE 가 아이콘과 "도쿄 3박 4일 · 댓글" 같은 꼬리표를 고른다.
 * PLACE_ADDED: 일정에 장소 추가. COMMENT_MENTION: 댓글에서 나를 멘션. POLL_CLOSED: 투표 확정.
 * AI_DRAFT_READY: AI 초안 완성. MEMBER_JOINED: 초대 수락.
 */
public enum NotificationType {
    PLACE_ADDED, COMMENT_MENTION, POLL_CLOSED, AI_DRAFT_READY, MEMBER_JOINED
}
