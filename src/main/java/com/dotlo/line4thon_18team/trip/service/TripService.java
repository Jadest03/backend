package com.dotlo.line4thon_18team.trip.service;

import com.dotlo.line4thon_18team.city.CityController;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.trip.dto.TripCreateRequest;
import com.dotlo.line4thon_18team.trip.dto.TripResponse;
import com.dotlo.line4thon_18team.trip.dto.TripUpdateRequest;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.entity.TripStatus;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripMemberRepository tripMemberRepository;

    @Transactional
    public TripResponse createTrip(Long userId, TripCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateSupportedCity(request.city());

        Trip trip = Trip.create(user, request.city(), request.startDate(), request.endDate(),
                request.companion(), request.companionCount());
        return TripResponse.from(tripRepository.save(trip));
    }

    // 내가 만든 여행 + 초대로 참여한 여행 (최신순)
    public List<TripResponse> getMyTrips(Long userId) {
        Stream<Trip> joined = tripMemberRepository.findAllByUserId(userId).stream().map(TripMember::getTrip);
        return Stream.concat(tripRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream(), joined)
                .sorted(Comparator.comparing(Trip::getCreatedAt).reversed())
                .map(TripResponse::from)
                .toList();
    }

    // 홈의 '이어서 만들기': 가장 최근에 손댄 작성 중 여행 (없으면 비어 있음)
    public Optional<TripResponse> getInProgressTrip(Long userId) {
        return tripRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, TripStatus.DRAFT)
                .map(TripResponse::from);
    }

    public TripResponse getTrip(Long userId, Long tripId) {
        return TripResponse.from(findMemberTrip(userId, tripId));
    }

    @Transactional
    public TripResponse updateTrip(Long userId, Long tripId, TripUpdateRequest request) {
        Trip trip = findMemberTrip(userId, tripId);
        if (request.city() != null) {
            validateSupportedCity(request.city());
        }
        trip.update(request.city(), request.startDate(), request.endDate(),
                request.companion(), request.companionCount());
        return TripResponse.from(trip);
    }

    /**
     * 내 여행을 찾고, 없거나 남의 여행이면 404 를 던진다.
     * 방장만 되는 기능에서 쓴다. 여행에 붙는 기능은 구성원 모두 같은 권한이라 findMemberTrip 을 쓴다.
     */
    public Trip findOwnedTrip(Long userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    }

    /**
     * 방장이거나 초대로 참여한 구성원이면 여행을 돌려주고, 아니면 404 를 던진다.
     * 여행에 붙는 기능(여행, 장소, 일정, 예약, 취향, 확정, 공유, 초대)에서 쓴다. 기획: 구성원 모두 같은 편집 권한
     */
    public Trip findMemberTrip(Long userId, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!isMember(trip, userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    public boolean isMember(Trip trip, Long userId) {
        return trip.getUser().getId().equals(userId) || tripMemberRepository.existsByTripIdAndUserId(trip.getId(), userId);
    }

    // 지원 도시 목록(GET /api/cities)에 있는 도시만 허용
    private void validateSupportedCity(String city) {
        if (CityController.findByName(city).isEmpty()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CITY);
        }
    }
}
