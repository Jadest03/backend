package com.dotlo.line4thon_18team.itinerary.validation;

import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.model.TripContext;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelLookup;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelQuery;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.result.DayWindow;
import com.dotlo.line4thon_18team.itinerary.validation.result.WindowAdjustment;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * 날짜별로 일정을 넣을 수 있는 시간을 계산한다.
 *
 * <ul>
 *   <li>평소: 취향의 시작 시각 ~ 하루 끝(숙소 복귀) 시각</li>
 *   <li>도착일: 도착 + 입국 + 공항→숙소 이동 + 짐 정리 이후부터</li>
 *   <li>출발일: 출국 수속 시각 − 숙소→공항 이동 − 짐 찾기 이전까지</li>
 * </ul>
 * 일정 생성(GEN-PLAN E2: "첫날은 도착 시간에 맞춰 가볍게 짰어요")에서도 그대로 쓴다.
 */
public final class DayWindowCalculator {

    private final TravelTimeProvider travel;
    private final ValidationPolicy policy;
    private final TravelEstimator estimator;

    public DayWindowCalculator(TravelTimeProvider travel, ValidationPolicy policy) {
        this.travel = Objects.requireNonNull(travel, "travel");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.estimator = new TravelEstimator(policy);
    }

    public List<DayWindow> calculate(TripContext trip, PreferenceProfile preferences) {
        LocalDateTime readyAfterArrival = trip.arrivalAt() == null ? null : readyAfterArrival(trip);
        LocalDateTime backAtHotelBy = trip.departureAt() == null ? null : backAtHotelBeforeDeparture(trip);

        List<DayWindow> windows = new ArrayList<>();
        for (LocalDate date = trip.startDate(); !date.isAfter(trip.endDate()); date = date.plusDays(1)) {
            LocalDateTime start = date.atTime(policy.dayStart(preferences.dayStart()));
            LocalDateTime end = date.atTime(policy.dayEnd());
            EnumSet<WindowAdjustment> adjustments = EnumSet.noneOf(WindowAdjustment.class);

            if (readyAfterArrival != null && readyAfterArrival.isAfter(start)) {
                start = readyAfterArrival.isAfter(end) ? end : readyAfterArrival;
                adjustments.add(WindowAdjustment.SHORTENED_BY_ARRIVAL);
            }
            if (backAtHotelBy != null && backAtHotelBy.isBefore(end)) {
                end = backAtHotelBy.isBefore(start) ? start : backAtHotelBy;
                adjustments.add(WindowAdjustment.SHORTENED_BY_DEPARTURE);
            }
            windows.add(new DayWindow(date, start, end, adjustments));
        }
        return windows;
    }

    private LocalDateTime readyAfterArrival(TripContext trip) {
        LocalDateTime leaveAirport = trip.arrivalAt().plusMinutes(policy.arrivalProcessingMinutes());
        int transfer = transferMinutes(trip, trip.startDate(), trip.arrivalAirport(),
                trip.endOf(trip.startDate()), leaveAirport);
        return leaveAirport.plusMinutes(transfer + policy.settleMinutes());
    }

    private LocalDateTime backAtHotelBeforeDeparture(TripContext trip) {
        LocalDateTime atAirportBy = trip.departureAt().minusMinutes(policy.departureCheckinMinutes());
        LocalDateTime roughDeparture = atAirportBy.minusMinutes(policy.defaultAirportTransferMinutes());
        int transfer = transferMinutes(trip, trip.endDate(), trip.startOf(trip.endDate()),
                trip.departureAirport(), roughDeparture);
        return atAirportBy.minusMinutes(transfer + policy.luggagePickupMinutes());
    }

    /*
     * 공항↔숙소 이동. 렌터카를 빌린 날은 운전으로 본다 (픽업·반납을 공항에서 하는 흐름).
     * 조회가 실패해 직선거리로 추정할 때도 같은 수단으로 맞춘다. 수단이 갈리면 마감 시각이 어긋난다.
     */
    private int transferMinutes(TripContext trip, LocalDate date, GeoPoint from, GeoPoint to,
                                LocalDateTime departAt) {
        if (from == null || to == null) {
            return policy.defaultAirportTransferMinutes();
        }
        TravelMode mode = trip.drivingOn(date) ? TravelMode.DRIVE : TravelMode.TRANSIT;
        TravelLookup lookup = TravelLookups.safe(travel,
                new TravelQuery(from, to, mode, departAt.atZone(trip.zone())));
        if (lookup.status() == TravelLookup.Status.OK) {
            return lookup.durationMinutes();
        }
        return estimator.estimateMinutes(TravelEstimator.straightLineMeters(from, to), mode);
    }
}
