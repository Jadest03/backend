package com.dotlo.line4thon_18team.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 숙소 저장 요청. 이 여행의 숙소 전체를 보낸 목록으로 바꾼다 (빈 목록이면 모두 지움).
 * 예) { "stays": [ { "placeId": "ChIJ...", "checkIn": "2026-05-18", "checkOut": "2026-05-20" } ] }
 */
public record StaysRequest(
        @NotNull(message = "stays는 필수입니다.")
        List<@Valid @NotNull Stay> stays
) {

    // placeId: 장소 검색 API 결과의 placeId. checkIn 날 밤부터 checkOut 전날 밤까지 묵는다
    public record Stay(
            @NotBlank(message = "placeId는 필수입니다.")
            String placeId,

            @NotNull(message = "checkIn은 필수입니다.")
            LocalDate checkIn,

            @NotNull(message = "checkOut은 필수입니다.")
            LocalDate checkOut
    ) {
    }
}
