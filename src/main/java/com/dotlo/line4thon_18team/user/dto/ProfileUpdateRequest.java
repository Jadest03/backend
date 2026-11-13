package com.dotlo.line4thon_18team.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 프로필 수정 요청. 화면의 값을 전부 보낸다.
 * 예) { "nickname": "예빈이", "bio": "안녕하세요!", "characterId": 1 }
 * 닉네임 형식(한글/영문/숫자 2~12자)과 중복은 서비스에서 확인한다.
 */
public record ProfileUpdateRequest(

        @NotNull(message = "닉네임은 필수입니다.")
        String nickname,

        @Size(max = 30, message = "한 줄 소개는 30자 이하여야 합니다.")
        String bio,

        @NotNull(message = "캐릭터는 필수입니다.")
        @Min(value = 1, message = "캐릭터 번호는 1~10입니다.")
        @Max(value = 10, message = "캐릭터 번호는 1~10입니다.")
        Integer characterId
) {
}
