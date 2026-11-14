package com.dotlo.line4thon_18team.trip.service;

import com.dotlo.line4thon_18team.chat.repository.ChatMessageRepository;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.interaction.service.ItemInteractionService;
import com.dotlo.line4thon_18team.itinerary.ItineraryTripCleanupService;
import com.dotlo.line4thon_18team.member.repository.TripInviteRepository;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.reservation.service.ReservationTripCleanupService;
import com.dotlo.line4thon_18team.share.repository.TripShareRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 삭제. 여행에 딸린 데이터를 모두 지운 뒤 여행을 지운다.
 * 일정/취향(BE3), 예약·좋아요·댓글(BE2)은 각 담당의 정리 메서드를 부르고, 나머지(장소, 채팅, 공유, 초대, 멤버)는 여기서 지운다.
 */
@Service
@RequiredArgsConstructor
public class TripDeleteService {

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final ItineraryTripCleanupService itineraryTripCleanupService;
    private final ReservationTripCleanupService reservationTripCleanupService;
    private final ItemInteractionService itemInteractionService;
    private final SavedPlaceRepository savedPlaceRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TripShareRepository tripShareRepository;
    private final TripInviteRepository tripInviteRepository;
    private final TripMemberRepository tripMemberRepository;

    /**
     * 방장만 지울 수 있다. 구성원이면 403, 구성원도 아니면 404.
     * 여행 행을 먼저 잠근다. 채팅 메시지·좋아요·댓글 저장도 같은 잠금을 잡아서, 정리하는 사이 새 행이 생겨
     * 여행 삭제가 외래키 오류로 실패하는 일이 없다. 잠근 뒤 다른 요청이 커밋한 행까지 지우도록 READ_COMMITTED
     * 로 연다 (나가기와 같은 이유).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteTrip(Long userId, Long tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .filter(found -> tripService.isMember(found, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
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
        itemInteractionService.deleteByTrip(tripId);
        savedPlaceRepository.deleteAllByTripId(tripId);
        chatMessageRepository.deleteAllByTripId(tripId);
        tripShareRepository.deleteByTripId(tripId);
        tripInviteRepository.deleteAllByTripId(tripId);
        tripMemberRepository.deleteAllByTripId(tripId);
        tripRepository.delete(trip);
    }
}
