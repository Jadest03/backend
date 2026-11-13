package com.dotlo.line4thon_18team.share.dto;

import com.dotlo.line4thon_18team.trip.entity.TripStatus;

/**
 * @param version            확정한 일정 버전 (최신 일정)
 * @param placesNeedingCheck 확인이 필요한 장소 수 ("확인이 필요한 장소 n곳")
 */
public record ConfirmResponse(Long tripId, TripStatus status, int version, int placesNeedingCheck) {
}
