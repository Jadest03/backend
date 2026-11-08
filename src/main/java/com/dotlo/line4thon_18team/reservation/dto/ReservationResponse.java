package com.dotlo.line4thon_18team.reservation.dto;

import com.dotlo.line4thon_18team.reservation.entity.TripRental;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.dotlo.line4thon_18team.reservation.entity.TripStay;

import java.time.LocalDate;
import java.util.List;

/**
 * 여행의 예약 정보 전체 (항공, 숙소, 렌터카). 저장 API도 저장한 뒤 이 모양을 돌려준다.
 * 없는 항공편·렌터카는 null.
 */
public record ReservationResponse(
        Long tripId,
        FlightsResponse flights,
        List<StayResponse> stays,
        RentalResponse rental
) {

    public record FlightsResponse(FlightInfo outbound, @JsonProperty("return") FlightInfo returnFlight) {
    }

    public record StayResponse(String placeId, String name, String address, double lat, double lng,
                               LocalDate checkIn, LocalDate checkOut) {

        public static StayResponse from(TripStay s) {
            return new StayResponse(s.getPlaceId(), s.getName(), s.getAddress(), s.getLatitude(), s.getLongitude(),
                    s.getCheckIn(), s.getCheckOut());
        }
    }

    // dates: 쓰는 날 (날짜순). placeId~lng: 픽업·반납 장소
    public record RentalResponse(List<LocalDate> dates, String placeId, String name, String address, double lat, double lng) {

        public static RentalResponse from(TripRental r) {
            return new RentalResponse(r.getDates().stream().sorted().toList(), r.getPlaceId(), r.getName(), r.getAddress(),
                    r.getLatitude(), r.getLongitude());
        }
    }
}
