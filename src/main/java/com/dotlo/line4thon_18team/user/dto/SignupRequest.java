package com.dotlo.line4thon_18team.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 가입 마무리 요청 (구글 로그인 다음 단계). 약관 동의와 프로필 설정을 한 번에 보낸다.
 * 예) { "agreements": { "terms": true, "privacy": true, "age14": true, "location": false },
 *       "nickname": "하늘", "characterId": 1 }
 */
public record SignupRequest(

        @NotNull(message = "약관 동의는 필수입니다.")
        @Valid
        Agreements agreements,

        @NotNull(message = "닉네임은 필수입니다.")
        String nickname,

        @NotNull(message = "캐릭터는 필수입니다.")
        @Min(value = 1, message = "캐릭터 번호는 1~10입니다.")
        @Max(value = 10, message = "캐릭터 번호는 1~10입니다.")
        Integer characterId
) {

    /**
     * @param terms    [필수] 서비스 이용약관
     * @param privacy  [필수] 개인정보 수집 및 이용
     * @param age14    [필수] 만 14세 이상
     * @param location [선택] 위치 정보 이용
     */
    public record Agreements(boolean terms, boolean privacy, boolean age14, boolean location) {

        public boolean allRequired() {
            return terms && privacy && age14;
        }
    }
}
