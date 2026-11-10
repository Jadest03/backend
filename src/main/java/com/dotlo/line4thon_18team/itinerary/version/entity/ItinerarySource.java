package com.dotlo.line4thon_18team.itinerary.version.entity;

/**
 * 이 버전을 만든 경로.
 * 검증기의 ValidationOrigin(충돌을 오류로 볼지 경고로 볼지)과는 다른 값이라 섞지 않는다.
 */
public enum ItinerarySource {
    AI_GENERATED,   // AI 일정 생성
    AI_EDITED,      // AI 수정 반영
    MANUAL_EDIT,    // 사용자가 직접 편집
    COURSE_ADD,     // 인기 코스 추가
    REVERT          // 이전 버전으로 되돌리기
}
