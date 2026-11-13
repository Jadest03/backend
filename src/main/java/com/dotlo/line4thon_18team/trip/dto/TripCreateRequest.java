package com.dotlo.line4thon_18team.trip.dto;

import com.dotlo.line4thon_18team.trip.entity.Companion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 여행 생성 요청.
 * 예) { "city": "오사카", "startDate": "2026-11-07", "endDate": "2026-11-09", "companion": "FRIEND", "companionCount": 3 }
 * companionCount 는 생략할 수 있다. (혼자 1명, 연인 2명은 자동, 친구/가족은 없으면 2명)
 */
public record TripCreateRequest(

        @NotBlank(message = "도시는 필수입니다.")
        @Size(max = 50, message = "도시 이름은 50자 이하여야 합니다.")
        String city,

        @NotNull(message = "시작일은 필수입니다.")
        LocalDate startDate,

        @NotNull(message = "종료일은 필수입니다.")
        LocalDate endDate,

        @NotNull(message = "동행은 필수입니다.")
        Companion companion,

        Integer companionCount
) {
}
