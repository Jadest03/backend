package com.dotlo.line4thon_18team.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 항공편 1개. 항공편 찾기 결과이면서, 항공권 저장 때 가는 편·오는 편으로 그대로 보내는 값.
 * 시각은 그 공항의 현지 시각. airline은 항공사 이름. 테스트 노선 항공사는 한국어(예: 대한항공), 나머지는
 * AeroDataBox가 주는 영어 이름 그대로이고, 없을 수 있음.
 */
public record FlightInfo(
        @NotBlank(message = "number는 필수입니다.")
        String number,

        String airline,

        @NotNull(message = "departure는 필수입니다.") @Valid
        Point departure,

        @NotNull(message = "arrival은 필수입니다.") @Valid
        Point arrival
) {

    // airport: 공항 IATA 코드 (예: ICN, KIX)
    public record Point(
            @NotBlank(message = "airport는 필수입니다.")
            String airport,

            @NotNull(message = "time은 필수입니다.")
            LocalDateTime time,

            double lat,
            double lng
    ) {
    }
}
