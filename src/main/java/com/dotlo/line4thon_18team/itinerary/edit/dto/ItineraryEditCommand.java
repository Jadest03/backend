package com.dotlo.line4thon_18team.itinerary.edit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 텍스트 수정 요청 바디.
 *
 * @param message 사용자가 적은 수정 요청. 예: "2일차 점심 빼고 오사카성 넣어 주세요"
 */
public record ItineraryEditCommand(
        @NotBlank(message = "message는 필수입니다.")
        @Size(max = 500, message = "수정 요청은 500자 이하로 입력해 주세요.")
        String message
) {
}
