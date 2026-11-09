package com.dotlo.line4thon_18team.reservation.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.place.PlaceMatcher.Candidate;
import com.dotlo.line4thon_18team.reservation.FlightClient;
import com.dotlo.line4thon_18team.reservation.dto.FlightInfo;
import com.dotlo.line4thon_18team.reservation.dto.ReservationResponse;
import com.dotlo.line4thon_18team.reservation.dto.ReservationResponse.FlightsResponse;
import com.dotlo.line4thon_18team.reservation.dto.ReservationResponse.RentalResponse;
import com.dotlo.line4thon_18team.reservation.dto.ReservationResponse.StayResponse;
import com.dotlo.line4thon_18team.reservation.dto.StaysRequest.Stay;
import com.dotlo.line4thon_18team.reservation.entity.FlightDirection;
import com.dotlo.line4thon_18team.reservation.entity.TripFlight;
import com.dotlo.line4thon_18team.reservation.entity.TripRental;
import com.dotlo.line4thon_18team.reservation.entity.TripStay;
import com.dotlo.line4thon_18team.reservation.repository.TripFlightRepository;
import com.dotlo.line4thon_18team.reservation.repository.TripRentalRepository;
import com.dotlo.line4thon_18team.reservation.repository.TripStayRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 여행의 예약 정보 (BE2): 항공, 숙소, 렌터카.
 * 일정 생성·검증이 항공 도착·출발 시각, 날짜별 숙소, 렌터카 쓰는 날을 기준으로 동선을 짠다.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    // 항공사 코드(영문 2자, 영문+숫자, 숫자+영문, 영문 3자) + 숫자 1~4자 (+ 끝 글자 하나). 예) KE723, W61234, 7C1301, KAL723
    // 코드에 영문이 없으면 "12345"가 "12"+"345"로 통과해서 AeroDataBox 한도를 씀 (#60 리뷰)
    private static final Pattern FLIGHT_NUMBER = Pattern.compile("(?:[A-Za-z][A-Za-z0-9]|\\d[A-Za-z]|[A-Za-z]{3})\\d{1,4}[A-Za-z]?");

    private final TripService tripService;
    private final PlaceMatcher placeMatcher;
    private final FlightClient flightClient;
    private final TripFlightRepository flightRepository;
    private final TripStayRepository stayRepository;
    private final TripRentalRepository rentalRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    public ReservationResponse get(Long userId, Long tripId) {
        tripService.findOwnedTrip(userId, tripId);   // 없는 여행·남의 여행이면 404
        return response(tripId);
    }

    // 항공편 찾기. 편명 모양이 아니면 AeroDataBox를 부르지 않는다 (없는 편명도 유닛을 씀)
    public List<FlightInfo> findFlights(String number, LocalDate date) {
        String n = number.replace(" ", "");
        if (!FLIGHT_NUMBER.matcher(n).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "편명 형식이 올바르지 않습니다. 예) KE723, 7C1301");
        }
        return flightClient.find(n, date);
    }

    // 가는 편·오는 편을 보낸 값으로 바꾼다 (null이면 없음). 찾기 결과를 그대로 받아서 AeroDataBox를 다시 부르지 않는다
    public ReservationResponse putFlights(Long userId, Long tripId, FlightInfo outbound, FlightInfo returnFlight) {
        tripService.findOwnedTrip(userId, tripId);
        return transactionTemplate.execute(status -> {
            Trip trip = lockTrip(tripId);
            flightRepository.deleteAllByTripId(tripId);
            entityManager.flush();   // 여행·방향별 1개(unique)라 새로 넣기 전에 지운 걸 먼저 반영
            if (outbound != null) flightRepository.save(TripFlight.of(trip, FlightDirection.OUTBOUND, outbound));
            if (returnFlight != null) flightRepository.save(TripFlight.of(trip, FlightDirection.RETURN, returnFlight));
            return response(tripId);
        });
    }

    // 숙소 전체를 보낸 목록으로 바꾼다. 구글(장소 정보)은 처음 들어온 숙소만 부르고, 기다리는 동안 DB 연결을 잡지 않는다
    public ReservationResponse putStays(Long userId, Long tripId, List<Stay> stays) {
        validateStays(tripService.findOwnedTrip(userId, tripId), stays);   // 구글 부르기 전에 날짜부터 확인

        Map<String, Candidate> places = new HashMap<>();
        for (TripStay s : stayRepository.findAllByTripIdOrderByCheckInAsc(tripId)) {
            places.put(s.getPlaceId(), new Candidate(s.getPlaceId(), s.getName(), s.getAddress(), s.getLatitude(), s.getLongitude()));
        }
        for (Stay s : stays) {
            places.computeIfAbsent(s.placeId(), id -> placeMatcher.details(null, id).place());   // 없는 placeId면 400
        }

        return transactionTemplate.execute(status -> {
            Trip trip = lockTrip(tripId);
            stayRepository.deleteAllByTripId(tripId);
            for (Stay s : stays) {
                Candidate c = places.get(s.placeId());
                stayRepository.save(TripStay.of(trip, c.placeId(), c.name(), c.address(), c.lat(), c.lng(), s.checkIn(), s.checkOut()));
            }
            return response(tripId);
        });
    }

    // 렌터카를 보낸 값으로 바꾼다. dates가 비면 지운다. 픽업 장소가 그대로면 구글을 다시 부르지 않는다
    public ReservationResponse putRental(Long userId, Long tripId, List<LocalDate> dates, String placeId) {
        Trip owned = tripService.findOwnedTrip(userId, tripId);
        Candidate place = null;
        if (!dates.isEmpty()) {
            boolean outOfTrip = dates.stream().anyMatch(d -> d.isBefore(owned.getStartDate()) || d.isAfter(owned.getEndDate()));
            if (outOfTrip || placeId == null || placeId.isBlank()) throw new BusinessException(ErrorCode.INVALID_RENTAL);
            place = rentalRepository.findByTripId(tripId)
                    .filter(r -> r.getPlaceId().equals(placeId))
                    .map(r -> new Candidate(r.getPlaceId(), r.getName(), r.getAddress(), r.getLatitude(), r.getLongitude()))
                    .orElseGet(() -> placeMatcher.details(null, placeId).place());   // 없는 placeId면 400
        }

        Candidate c = place;
        return transactionTemplate.execute(status -> {
            Trip trip = lockTrip(tripId);
            rentalRepository.deleteByTripId(tripId);
            entityManager.flush();   // 여행당 1개(trip_id unique)라 새로 넣기 전에 지운 걸 먼저 반영
            if (c != null) {
                rentalRepository.save(TripRental.of(trip, new HashSet<>(dates), c.placeId(), c.name(), c.address(), c.lat(), c.lng()));
            }
            return response(tripId);
        });
    }

    // 여행 행을 잠가서 같은 여행의 저장을 한 번에 하나씩 처리한다.
    // 안 잠그면 동시에 저장할 때 두 요청이 같은 기존 행을 지우려다 늦은 쪽이 500
    // (2026-11-08 잠금 없이 동시 2번: 숙소 15번 중 8번, 렌터카 15번 중 15번)
    private Trip lockTrip(Long tripId) {
        return entityManager.find(Trip.class, tripId, LockModeType.PESSIMISTIC_WRITE);
    }

    // 여행 기간 안, checkIn < checkOut, 숙소끼리 밤이 겹치지 않게. 비는 밤은 괜찮다 (예약 안 한 밤)
    private static void validateStays(Trip trip, List<Stay> stays) {
        LocalDate prevCheckOut = trip.getStartDate();
        for (Stay s : stays.stream().sorted(Comparator.comparing(Stay::checkIn)).toList()) {
            if (!s.checkIn().isBefore(s.checkOut()) || s.checkIn().isBefore(prevCheckOut) || s.checkOut().isAfter(trip.getEndDate())) {
                throw new BusinessException(ErrorCode.INVALID_STAY_DATES);
            }
            prevCheckOut = s.checkOut();
        }
    }

    private ReservationResponse response(Long tripId) {
        List<StayResponse> stays = stayRepository.findAllByTripIdOrderByCheckInAsc(tripId).stream()
                .map(StayResponse::from)
                .toList();
        RentalResponse rental = rentalRepository.findByTripId(tripId).map(RentalResponse::from).orElse(null);
        Map<FlightDirection, FlightInfo> flights = new EnumMap<>(FlightDirection.class);
        for (TripFlight f : flightRepository.findAllByTripId(tripId)) flights.put(f.getDirection(), f.toInfo());
        return new ReservationResponse(tripId,
                new FlightsResponse(flights.get(FlightDirection.OUTBOUND), flights.get(FlightDirection.RETURN)), stays, rental);
    }
}
