package com.dotlo.line4thon_18team.member.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.trip.service.TripDeleteService;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 여행 나가기. 회원 탈퇴도 내 여행마다 이걸 부른다.
 * 멤버는 그냥 빠지고, 방장이 나가면 가장 먼저 참여한 멤버가 방장이 된다.
 * 방장 혼자인 여행은 나가면 볼 사람이 없어서 여행을 지운다.
 */
@Service
@RequiredArgsConstructor
public class TripLeaveService {

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripDeleteService tripDeleteService;

    /**
     * 초대 수락과 같은 여행 행을 잠가서, 나가는 사이에 누가 들어오거나 방장이 바뀌는 일이 겹치지 않게 한다.
     * 잠근 뒤 다른 요청이 커밋한 멤버가 보이도록 READ_COMMITTED 로 연다 (수락과 같은 이유).
     *
     * @return 여행이 지워졌으면 true
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean leave(Long tripId, Long userId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!tripService.isMember(trip, userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }

        if (!trip.getUser().getId().equals(userId)) {
            tripMemberRepository.findByTripIdAndUserId(tripId, userId).ifPresent(tripMemberRepository::delete);
            return false;
        }

        Optional<TripMember> next = tripMemberRepository.findFirstByTripIdOrderByIdAsc(tripId);
        if (next.isEmpty()) {
            tripDeleteService.delete(trip);
            return true;
        }
        trip.changeOwner(next.get().getUser());
        tripMemberRepository.delete(next.get());   // 방장은 trip_member 에 두지 않는다
        return false;
    }
}
