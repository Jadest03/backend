package com.dotlo.line4thon_18team.user.dto;

/**
 * 마이 홈 숫자 3개. 내가 만든 여행과 초대로 참여한 여행을 모두 센다.
 *
 * @param savedPlaceCount    여행들에 저장된 장소 수
 * @param savedCourseCount   찜한 인기 코스 수 (찜 기능이 생기기 전까지 0)
 * @param completedTripCount 종료일이 지난 여행 수
 */
public record MyStatsResponse(long savedPlaceCount, long savedCourseCount, long completedTripCount) {
}
