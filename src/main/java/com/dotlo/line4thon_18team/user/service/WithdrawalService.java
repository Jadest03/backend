package com.dotlo.line4thon_18team.user.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.member.service.TripLeaveService;
import com.dotlo.line4thon_18team.preference.PreferenceRepository;
import com.dotlo.line4thon_18team.preference.UserPreferenceRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.dto.WithdrawalPreviewResponse;
import com.dotlo.line4thon_18team.user.dto.WithdrawalPreviewResponse.TripItem;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 회원 탈퇴. 내 여행마다 "나가기"를 한 뒤(방장이면 다음 멤버에게 넘기고, 혼자면 삭제) 개인정보를 지운다.
 * 이미 받은 access 토큰은 만료(30분)까지 남지만, refresh 토큰을 지워서 재발급은 안 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithdrawalService {

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripLeaveService tripLeaveService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PreferenceRepository preferenceRepository;

    public WithdrawalPreviewResponse preview(Long userId) {
        findUser(userId);
        List<TripItem> deleted = new ArrayList<>();
        List<TripItem> transferred = new ArrayList<>();
        for (Trip trip : tripRepository.findAllByUserIdOrderByCreatedAtDesc(userId)) {
            Optional<TripMember> next = tripMemberRepository.findFirstByTripIdOrderByIdAsc(trip.getId());
            if (next.isEmpty()) {
                deleted.add(item(trip, null));
            } else {
                transferred.add(item(trip, next.get().getUser().getDisplayName()));
            }
        }
        List<TripItem> left = tripMemberRepository.findAllByUserId(userId).stream()
                .map(member -> item(member.getTrip(), null))
                .toList();
        return new WithdrawalPreviewResponse(deleted, transferred, left);
    }

    // 나가기와 같은 이유로 READ_COMMITTED (안에서 부르는 leave 가 이 트랜잭션에 합류한다)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Long userId) {
        User user = findUser(userId);
        List<Long> tripIds = new ArrayList<>();
        tripRepository.findAllByUserIdOrderByCreatedAtDesc(userId).forEach(trip -> tripIds.add(trip.getId()));
        tripMemberRepository.findAllByUserId(userId).forEach(member -> tripIds.add(member.getTrip().getId()));
        for (Long tripId : tripIds) {
            tripLeaveService.leave(tripId, userId);
        }
        userPreferenceRepository.deleteByUser(userId);
        preferenceRepository.deleteByUser(userId);
        user.withdraw(LocalDateTime.now());
    }

    private static TripItem item(Trip trip, String newOwnerName) {
        return new TripItem(trip.getId(), trip.getCity(), trip.getStartDate(), trip.getEndDate(), newOwnerName);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
