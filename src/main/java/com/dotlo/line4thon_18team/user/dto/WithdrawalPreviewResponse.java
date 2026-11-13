package com.dotlo.line4thon_18team.user.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 탈퇴하기 전에 보여줄 안내. 탈퇴하면 내 여행이 어떻게 되는지 나눠서 알려준다.
 *
 * @param deletedTrips     멤버 없이 혼자 쓰던 여행. 같이 삭제된다
 * @param transferredTrips 멤버가 있는 내 여행. 가장 먼저 참여한 멤버가 방장이 된다 (newOwnerName)
 * @param leftTrips        초대받아 참여한 여행. 멤버에서 빠진다
 */
public record WithdrawalPreviewResponse(List<TripItem> deletedTrips, List<TripItem> transferredTrips,
                                        List<TripItem> leftTrips) {

    /** @param newOwnerName 새 방장 이름 (transferredTrips 에만 있다) */
    public record TripItem(Long tripId, String city, LocalDate startDate, LocalDate endDate, String newOwnerName) {
    }
}
