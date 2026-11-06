package com.dotlo.line4thon_18team.place.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 후보 선택·장소 직접 추가 요청. placeId 는 사용자가 고른 구글 장소 ID (장소 검색 API 결과의 placeId).
 * 예) { "placeId": "ChIJt9Y375HmAGARejKVNQwlkbI" }
 */
public record GooglePlaceRequest(
        @NotBlank(message = "placeId는 필수입니다.")
        String placeId
) {
}
