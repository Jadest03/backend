package com.dotlo.line4thon_18team.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

/**
 * 항공권 저장 요청. 가는 편·오는 편을 보낸 값으로 바꾼다 (null이면 없음, 둘 다 null이면 모두 지움).
 * 값은 항공편 찾기(GET /api/flights) 결과를 그대로 보낸다.
 * 예) { "outbound": { ...찾기 결과... }, "return": null }
 */
public record FlightsRequest(
        @Valid
        FlightInfo outbound,

        @JsonProperty("return") @Valid
        FlightInfo returnFlight
) {
}
