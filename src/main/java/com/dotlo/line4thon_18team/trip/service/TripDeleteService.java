package com.dotlo.line4thon_18team.trip.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.ItineraryTripCleanupService;
import com.dotlo.line4thon_18team.member.repository.TripInviteRepository;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.notification.repository.NotificationRepository;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.reservation.service.ReservationTripCleanupService;
import com.dotlo.line4thon_18team.share.repository.TripShareRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 삭제. 여행에 딸린 데이터를 모두 지운 뒤 여행을 지운다.
 * 일정/취향(BE3), 예약(BE2)은 각 담당의 정리 메서드를 부르고, 나머지(장소, 알림, 공유, 초대, 멤버)는 여기서 지운다.
 */
@Service
@RequiredArgsConstructor
public class TripDeleteService {

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final ItineraryTripCleanupService itineraryTripCleanupService;
    private final ReservationTripCleanupService reservationTripCleanupService;
    private final SavedPlaceRepository savedPlaceRepository;
    private final NotificationRepository notificationRepository;
    private final TripShareRepository tripShareRepository;
    private final TripInviteRepository tripInviteRepository;
    private final TripMemberRepository tripMemberRepository;

    // 방장만 지울 수 있다. 구성원이면 403, 구성원도 아니면 404
    @Transactional
    public void deleteTrip(Long userId, Long tripId) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        if (!trip.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.TRIP_OWNER_ONLY);
        }
        delete(trip);
    }

    // 권한 확인 없이 지운다 (혼자 남은 방장이 나가거나 탈퇴할 때)
    @Transactional
    public void delete(Trip trip) {
        Long tripId = trip.getId();
        itineraryTripCleanupService.deleteByTrip(tripId);
        reservationTripCleanupService.deleteByTrip(tripId);
        savedPlaceRepository.deleteAllByTripId(tripId);
        notificationRepository.deleteAllByTripId(tripId);
        tripShareRepository.deleteByTripId(tripId);
        tripInviteRepository.deleteAllByTripId(tripId);
        tripMemberRepository.deleteAllByTripId(tripId);
        tripRepository.delete(trip);
    }
}
