package com.dotlo.line4thon_18team.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 렌터카 저장 요청. dates가 빈 목록이면 렌터카를 지운다 (placeId는 보지 않음).
 * 예) { "dates": ["2026-05-19", "2026-05-20"], "placeId": "ChIJ..." }
 */
public record RentalRequest(
        @NotNull(message = "dates는 필수입니다.")
        List<@NotNull LocalDate> dates,

        // 픽업·반납 장소. 장소 검색 API 결과의 placeId
        String placeId
) {
}
