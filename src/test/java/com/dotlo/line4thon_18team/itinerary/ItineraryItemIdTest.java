package com.dotlo.line4thon_18team.itinerary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 일정 항목 ID 형식. BE1 저장 쪽도 같은 함수를 쓰므로 형식이 바뀌면 안 된다. */
class ItineraryItemIdTest {

    @Test
    void v_접두어에_16진수_8자리다() {
        assertThat(ItineraryItemId.newId()).matches("v-[0-9a-f]{8}");
    }

    @Test
    void 부를_때마다_다른_값이_나온다() {
        assertThat(ItineraryItemId.newId()).isNotEqualTo(ItineraryItemId.newId());
    }
}
