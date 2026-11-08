package com.dotlo.line4thon_18team.reservation.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.reservation.dto.FlightInfo;
import com.dotlo.line4thon_18team.reservation.dto.FlightInfo.Point;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 여행의 항공편 (BE2). 여행마다 가는 편·오는 편 각 1개까지. 시각은 그 공항의 현지 시각.
 */
@Entity
@Table(
        name = "trip_flights",
        uniqueConstraints = @UniqueConstraint(name = "uk_trip_flight_direction", columnNames = {"trip_id", "direction"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripFlight extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private FlightDirection direction;

    // 편명 (예: KE723)
    @Column(name = "flight_number", nullable = false, length = 20)
    private String number;

    @Column(length = 100)
    private String airline;

    @Column(nullable = false, length = 10)
    private String departureAirport;

    @Column(nullable = false)
    private LocalDateTime departureAt;

    @Column(nullable = false)
    private double departureLatitude;

    @Column(nullable = false)
    private double departureLongitude;

    @Column(nullable = false, length = 10)
    private String arrivalAirport;

    @Column(nullable = false)
    private LocalDateTime arrivalAt;

    @Column(nullable = false)
    private double arrivalLatitude;

    @Column(nullable = false)
    private double arrivalLongitude;

    public static TripFlight of(Trip trip, FlightDirection direction, FlightInfo f) {
        TripFlight flight = new TripFlight();
        flight.trip = trip;
        flight.direction = direction;
        flight.number = f.number();
        flight.airline = f.airline();
        flight.departureAirport = f.departure().airport();
        flight.departureAt = f.departure().time();
        flight.departureLatitude = f.departure().lat();
        flight.departureLongitude = f.departure().lng();
        flight.arrivalAirport = f.arrival().airport();
        flight.arrivalAt = f.arrival().time();
        flight.arrivalLatitude = f.arrival().lat();
        flight.arrivalLongitude = f.arrival().lng();
        return flight;
    }

    public FlightInfo toInfo() {
        return new FlightInfo(number, airline,
                new Point(departureAirport, departureAt, departureLatitude, departureLongitude),
                new Point(arrivalAirport, arrivalAt, arrivalLatitude, arrivalLongitude));
    }
}
