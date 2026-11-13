package com.dotlo.line4thon_18team.member.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.member.dto.InvitePreviewResponse;
import com.dotlo.line4thon_18team.member.dto.InviteResponse;
import com.dotlo.line4thon_18team.member.dto.MemberResponse;
import com.dotlo.line4thon_18team.member.dto.MemberResponse.Role;
import com.dotlo.line4thon_18team.member.entity.TripInvite;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripInviteRepository;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.trip.service.TripService;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InviteService {

    static final int MAX_MEMBERS = 10;                      // 방장 포함
    static final Duration VALID_FOR = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final TripInviteRepository tripInviteRepository;
    private final TripMemberRepository tripMemberRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final UserRepository userRepository;

    // 구성원 누구나 만들 수 있다. 만료 전 링크가 있으면 그 링크를 다시 준다
    @Transactional
    public InviteResponse createInvite(Long tripId, Long userId) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        LocalDateTime now = LocalDateTime.now();
        TripInvite invite = tripInviteRepository.findFirstByTripIdAndExpiresAtAfterOrderByExpiresAtDesc(tripId, now)
                .orElseGet(() -> tripInviteRepository.save(
                        TripInvite.create(trip, newToken(), userId, now.plus(VALID_FOR))));
        return new InviteResponse(invite.getToken(), invite.getExpiresAt());
    }

    // 로그인 없이 보는 초대 미리보기
    public InvitePreviewResponse preview(String token) {
        TripInvite invite = findValidInvite(token);
        Trip trip = invite.getTrip();
        String inviterName = userRepository.findById(invite.getCreatedBy()).map(User::getDisplayName).orElse(null);
        return new InvitePreviewResponse(trip.getCity(), trip.getStartDate(), trip.getEndDate(), trip.getCompanion(),
                inviterName, memberCount(trip), MAX_MEMBERS, savedPlaceRepository.countByTripId(trip.getId()),
                invite.getExpiresAt());
    }

    // 이미 구성원(방장 포함)이면 그대로 통과한다. 돌려주는 값은 들어갈 여행 id
    // 마지막 자리에 두 명이 동시에 수락해도 넘치지 않게 여행 행을 잠근 뒤 인원을 센다
    // 기본 격리 수준(REPEATABLE READ)은 잠그기 전 시점 기준으로 세서, 다른 수락이 커밋한 인원이 보이도록 READ_COMMITTED 로 연다
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long accept(String token, Long userId) {
        Long tripId = findValidInvite(token).getTrip().getId();
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (tripService.isMember(trip, userId)) {
            return trip.getId();
        }
        if (memberCount(trip) >= MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.TRIP_MEMBER_FULL);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        tripMemberRepository.saveAndFlush(TripMember.create(trip, user));
        return trip.getId();
    }

    // 방장이 맨 앞, 그다음 참여한 순서
    public List<MemberResponse> getMembers(Long tripId, Long userId) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        User owner = trip.getUser();
        List<MemberResponse> members = new ArrayList<>();
        members.add(new MemberResponse(owner.getId(), owner.getDisplayName(), owner.getProfileImageUrl(),
                owner.getCharacterId(), Role.OWNER, owner.getId().equals(userId), trip.getCreatedAt()));
        for (TripMember member : tripMemberRepository.findAllByTripIdOrderByIdAsc(tripId)) {
            User user = member.getUser();
            members.add(new MemberResponse(user.getId(), user.getDisplayName(), user.getProfileImageUrl(),
                    user.getCharacterId(), Role.MEMBER, user.getId().equals(userId), member.getCreatedAt()));
        }
        return members;
    }

    private TripInvite findValidInvite(String token) {
        TripInvite invite = tripInviteRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_NOT_FOUND));
        if (invite.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_EXPIRED);
        }
        return invite;
    }

    private int memberCount(Trip trip) {
        return 1 + (int) tripMemberRepository.countByTripId(trip.getId());
    }

    // 16바이트 랜덤 → URL에 쓸 수 있는 22글자
    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
