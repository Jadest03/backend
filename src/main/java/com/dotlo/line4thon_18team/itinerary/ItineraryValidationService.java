package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.city.CityController;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.validation.ItineraryValidator;
import com.dotlo.line4thon_18team.itinerary.validation.ValidationPolicy;
import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.TripContext;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.place.OpeningHours;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.reservation.entity.FlightDirection;
import com.dotlo.line4thon_18team.reservation.entity.TripFlight;
import com.dotlo.line4thon_18team.reservation.entity.TripRental;
import com.dotlo.line4thon_18team.reservation.entity.TripStay;
import com.dotlo.line4thon_18team.reservation.repository.TripFlightRepository;
import com.dotlo.line4thon_18team.reservation.repository.TripRentalRepository;
import com.dotlo.line4thon_18team.reservation.repository.TripStayRepository;
import com.dotlo.line4thon_18team.preference.PreferenceService;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 일정 검증 (GEN-CHECK-001). 방문 순서만 받아서 시각을 계산하고 영업시간·이동시간 문제를 찾는다.
 *
 * <p>일정 생성·수정(BE3)뿐 아니라 직접 편집 저장(BE1), 인기 코스 담기(BE2)에서도 이 함수를 부르면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryValidationService {

    private final TripService tripService;
    private final SavedPlaceRepository savedPlaceRepository;
    private final PreferenceService preferenceService;
    private final OpeningHours openingHours;          // BE2 영업시간
    private final TripStayRepository tripStayRepository;      // BE2 숙소
    private final TripFlightRepository tripFlightRepository;  // BE2 항공편
    private final TripRentalRepository tripRentalRepository;  // BE2 렌터카
    private final TravelTimeProvider travelTimeProvider;
    private final ValidationPolicy validationPolicy;

    /**
     * @param origin AI가 만든 일정이면 AI_GENERATED(일정 충돌을 오류로), 사용자가 직접 고친 일정이면 USER_EDITED(경고로)
     * @throws BusinessException 남의 여행·없는 여행(404), 일정 입력이 잘못된 경우(400)
     */
    public ValidationResult validate(long tripId, long userId, ItineraryDraft draft, ValidationOrigin origin) {
        return prepare(tripId, userId).validate(draft, origin);
    }

    /**
     * 여행·저장 장소·취향을 한 번만 읽어두고 여러 번 검증할 때 쓴다 (일정 생성에서 재생성할 때).
     *
     * @throws BusinessException 남의 여행·없는 여행(404)
     */
    ValidationRun prepare(long tripId, long userId) {
        Trip trip = tripService.findOwnedTrip(userId, tripId);
        List<SavedPlace> places = savedPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        ItineraryValidator validator = new ItineraryValidator(
                new SavedPlaceInfoProvider(places, openingHours::get), travelTimeProvider, validationPolicy);
        return new ValidationRun(trip, places, validator, tripContext(trip, tripId),
                preferenceService.profileOrDefault(tripId, userId));
    }

    /** 검증 한 판에 필요한 것들. 같은 여행을 여러 번 검증해도 조회는 한 번만 한다. */
    record ValidationRun(Trip trip,
                         List<SavedPlace> places,
                         ItineraryValidator validator,
                         TripContext context,
                         PreferenceProfile preferences) {

        /** @throws BusinessException 일정 입력이 잘못된 경우(400) */
        ValidationResult validate(ItineraryDraft draft, ValidationOrigin origin) {
            try {
                return validator.validate(draft, context, preferences, origin);
            } catch (IllegalArgumentException e) {
                // 여행 기간 밖 날짜, 같은 날짜 중복, itemId 중복
                throw new BusinessException(ErrorCode.INVALID_ITINERARY,
                        ErrorCode.INVALID_ITINERARY.getMessage() + " " + e.getMessage());
            }
        }
    }

    /** 숙소를 하나도 입력하지 않았으면 도시 중심을 기준으로 계산한다. */
    private TripContext tripContext(Trip trip, long tripId) {
        CityController.City city = CityController.findByName(trip.getCity())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_CITY));
        GeoPoint cityCenter = new GeoPoint(city.lat(), city.lng());
        Map<LocalDate, GeoPoint> nights = nightlyStays(trip, tripStayRepository.findAllByTripIdOrderByCheckInAsc(tripId));
        Flights flights = flights(tripFlightRepository.findAllByTripId(tripId));
        return new TripContext(ZoneId.of(city.timezone()), trip.getStartDate(), trip.getEndDate(),
                nights, cityCenter, flights.arrivalAirport(), flights.departureAirport(),
                rentalDays(tripId), flights.arrivalAt(), flights.departureAt());
    }

    /*
     * 숙소는 checkIn 날 밤부터 checkOut 전날 밤까지다 (BE2 trip_stays).
     * 하나라도 넣었으면 첫날부터 마지막 날 전날까지 모든 밤이 채워져 있어야 한다. 한 밤이 비면 그날
     * 출발·도착 좌표가 없어서 동선을 짤 수 없고, 도시 중심으로 때우면 다른 도시 일정이 통째로 어긋난다.
     * 하나도 안 넣은 경우는 막지 않는다 (숙소 없이 일정부터 보는 흐름).
     */
    static Map<LocalDate, GeoPoint> nightlyStays(Trip trip, List<TripStay> stays) {
        if (stays.isEmpty()) {
            return Map.of();
        }
        Map<LocalDate, GeoPoint> byNight = new HashMap<>();
        for (TripStay stay : stays) {
            GeoPoint place = new GeoPoint(stay.getLatitude(), stay.getLongitude());
            for (LocalDate night = stay.getCheckIn(); night.isBefore(stay.getCheckOut()); night = night.plusDays(1)) {
                byNight.put(night, place);
            }
        }
        for (LocalDate night = trip.getStartDate(); night.isBefore(trip.getEndDate()); night = night.plusDays(1)) {
            if (!byNight.containsKey(night)) {
                throw new BusinessException(ErrorCode.STAY_REQUIRED);
            }
        }
        return byNight;
    }

    /** 렌터카를 빌린 날. 그 날은 걷기 기준을 넘는 구간을 운전으로 계산한다. */
    private Set<LocalDate> rentalDays(long tripId) {
        return tripRentalRepository.findByTripId(tripId)
                .map(TripRental::getDates)
                .map(Set::copyOf)
                .orElseGet(Set::of);
    }

    /*
     * 들어오는 공항은 가는 편이 내리는 곳, 나가는 공항은 오는 편이 타는 곳이다.
     * 간사이로 들어와 이타미로 나가는 경우처럼 둘이 다를 수 있어서 따로 담는다.
     * 한쪽 항공편만 등록했으면 그 공항만 채우고, 나머지는 기본 이동시간으로 계산한다.
     */
    static Flights flights(List<TripFlight> flights) {
        GeoPoint arrivalAirport = null;
        GeoPoint departureAirport = null;
        LocalDateTime arrivalAt = null;
        LocalDateTime departureAt = null;
        for (TripFlight flight : flights) {
            if (flight.getDirection() == FlightDirection.OUTBOUND) {
                arrivalAirport = new GeoPoint(flight.getArrivalLatitude(), flight.getArrivalLongitude());
                arrivalAt = flight.getArrivalAt();
            } else {
                departureAirport = new GeoPoint(flight.getDepartureLatitude(), flight.getDepartureLongitude());
                departureAt = flight.getDepartureAt();
            }
        }
        return new Flights(arrivalAirport, departureAirport, arrivalAt, departureAt);
    }

    record Flights(GeoPoint arrivalAirport, GeoPoint departureAirport,
                   LocalDateTime arrivalAt, LocalDateTime departureAt) {
    }
}
