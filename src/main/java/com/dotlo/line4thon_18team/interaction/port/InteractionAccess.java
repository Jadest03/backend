package com.dotlo.line4thon_18team.interaction.port;

import java.util.Set;

/**
 * BE1 일정 저장·동행 기능 연결 지점. 실제 구현 전에는 서비스를 빈으로 등록하지 않는다.
 * 여행 소유자 또는 수락된 동행자인지 먼저 검사하고, 최신 저장 일정의 항목 ID만 반환해야 한다.
 * 없는 여행·권한 없는 사용자는 BusinessException(TRIP_NOT_FOUND)을 던진다.
 * 탈퇴·초대 대기·공유 링크 열람자는 접근 권한이 없다. 빈 일정은 빈 집합이다.
 * 쓰기에서는 여행 행 잠금을 잡은 같은 트랜잭션 안에서 호출된다.
 */
public interface InteractionAccess {
    Set<String> activeItemIds(Long userId, Long tripId);
}
