package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.reservation.entity.FlightDirection;
import com.dotlo.line4thon_18team.reservation.entity.TripFlight;
import com.dotlo.line4thon_18team.reservation.entity.TripStay;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 숙소를 밤별로 펼치는 규칙. 하나라도 넣었으면 모든 밤이 채워져 있어야 한다. */
class ItineraryValidationServiceTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 12, 14);
    private static final LocalDate DAY4 = DAY1.plusDays(3);

    @Test
    void 숙소를_하나도_넣지_않으면_빈_채로_둔다() {
        assertThat(ItineraryValidationService.nightlyStays(trip(), List.of())).isEmpty();
    }

    @Test
    void 체크인_날_밤부터_체크아웃_전날_밤까지_채운다() {
        // 12/14 ~ 12/17 = 14·15·16일 밤 3박
        Map<LocalDate, GeoPoint> nights =
                ItineraryValidationService.nightlyStays(trip(), List.of(stay(DAY1, DAY4, 34.66, 135.50)));

        assertThat(nights).containsOnlyKeys(DAY1, DAY1.plusDays(1), DAY1.plusDays(2));
        assertThat(nights.get(DAY1)).isEqualTo(new GeoPoint(34.66, 135.50));
    }

    @Test
    void 숙소가_둘이면_밤마다_다른_좌표가_들어간다() {
        Map<LocalDate, GeoPoint> nights = ItineraryValidationService.nightlyStays(trip(), List.of(
                stay(DAY1, DAY1.plusDays(2), 34.66, 135.50),
                stay(DAY1.plusDays(2), DAY4, 34.70, 135.49)));

        assertThat(nights.get(DAY1)).isEqualTo(new GeoPoint(34.66, 135.50));
        assertThat(nights.get(DAY1.plusDays(1))).isEqualTo(new GeoPoint(34.66, 135.50));
        assertThat(nights.get(DAY1.plusDays(2))).isEqualTo(new GeoPoint(34.70, 135.49));
    }

    @Test
    void 비는_밤이_있으면_400이다() {
        // 14일 밤만 있고 15·16일 밤이 없다
        assertThatThrownBy(() -> ItineraryValidationService.nightlyStays(trip(),
                List.of(stay(DAY1, DAY1.plusDays(1), 34.66, 135.50))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("숙소를 입력하지 않은 밤");
    }

    @Test
    void 마지막_날_밤은_없어도_된다() {
        // 떠나는 날 밤은 묵지 않는다
        assertThat(ItineraryValidationService.nightlyStays(trip(), List.of(stay(DAY1, DAY4, 34.66, 135.50))))
                .doesNotContainKey(DAY4);
    }

    @Test
    void 가는_편은_내리는_공항_오는_편은_타는_공항을_쓴다() {
        ItineraryValidationService.Flights flights = ItineraryValidationService.flights(List.of(
                outbound(34.7855, 135.4382, DAY1.atTime(13, 0)),
                inbound(34.4347, 135.2440, DAY4.atTime(15, 0))));

        assertThat(flights.arrivalAirport()).isEqualTo(new GeoPoint(34.7855, 135.4382));
        assertThat(flights.departureAirport()).isEqualTo(new GeoPoint(34.4347, 135.2440));
        assertThat(flights.arrivalAt()).isEqualTo(DAY1.atTime(13, 0));
        assertThat(flights.departureAt()).isEqualTo(DAY4.atTime(15, 0));
    }

    @Test
    void 한쪽_항공편만_있으면_그_공항만_채운다() {
        ItineraryValidationService.Flights onlyOutbound =
                ItineraryValidationService.flights(List.of(outbound(34.7855, 135.4382, DAY1.atTime(13, 0))));

        assertThat(onlyOutbound.arrivalAirport()).isNotNull();
        assertThat(onlyOutbound.departureAirport()).isNull();
        assertThat(onlyOutbound.departureAt()).isNull();
    }

    @Test
    void 항공편이_없으면_전부_비어_있다() {
        ItineraryValidationService.Flights none = ItineraryValidationService.flights(List.of());

        assertThat(none.arrivalAirport()).isNull();
        assertThat(none.departureAirport()).isNull();
        assertThat(none.arrivalAt()).isNull();
        assertThat(none.departureAt()).isNull();
    }

    private static TripFlight outbound(double lat, double lng, java.time.LocalDateTime arrivalAt) {
        TripFlight flight = Mockito.mock(TripFlight.class);
        Mockito.when(flight.getDirection()).thenReturn(FlightDirection.OUTBOUND);
        Mockito.when(flight.getArrivalLatitude()).thenReturn(lat);
        Mockito.when(flight.getArrivalLongitude()).thenReturn(lng);
        Mockito.when(flight.getArrivalAt()).thenReturn(arrivalAt);
        return flight;
    }

    private static TripFlight inbound(double lat, double lng, java.time.LocalDateTime departureAt) {
        TripFlight flight = Mockito.mock(TripFlight.class);
        Mockito.when(flight.getDirection()).thenReturn(FlightDirection.RETURN);
        Mockito.when(flight.getDepartureLatitude()).thenReturn(lat);
        Mockito.when(flight.getDepartureLongitude()).thenReturn(lng);
        Mockito.when(flight.getDepartureAt()).thenReturn(departureAt);
        return flight;
    }

    private static Trip trip() {
        Trip trip = Mockito.mock(Trip.class);
        Mockito.when(trip.getStartDate()).thenReturn(DAY1);
        Mockito.when(trip.getEndDate()).thenReturn(DAY4);
        return trip;
    }

    private static TripStay stay(LocalDate checkIn, LocalDate checkOut, double lat, double lng) {
        TripStay stay = Mockito.mock(TripStay.class);
        Mockito.when(stay.getCheckIn()).thenReturn(checkIn);
        Mockito.when(stay.getCheckOut()).thenReturn(checkOut);
        Mockito.when(stay.getLatitude()).thenReturn(lat);
        Mockito.when(stay.getLongitude()).thenReturn(lng);
        return stay;
    }
}
