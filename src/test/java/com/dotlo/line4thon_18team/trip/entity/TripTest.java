package com.dotlo.line4thon_18team.trip.entity;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Test
    void 친구_가족은_인원을_안_보내면_2명이다() {
        Trip trip = Trip.create(null, "오사카", START, START.plusDays(1), Companion.FRIEND, null);

        assertThat(trip.getCompanionCount()).isEqualTo(2);
    }

    @Test
    void 인원을_보냈는데_범위_밖이면_에러() {
        assertThatThrownBy(() -> Trip.create(null, "오사카", START, START.plusDays(1), Companion.FAMILY, 11))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COMPANION_COUNT);
    }
}
