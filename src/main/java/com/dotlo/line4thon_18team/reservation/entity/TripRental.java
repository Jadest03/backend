package com.dotlo.line4thon_18team.reservation.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 여행의 렌터카 (BE2). 여행당 1개. 쓰는 날은 이어지지 않아도 되고, 픽업·반납 장소는 한 곳.
 * 쓰는 날은 차로 이동하는 코스로 짠다.
 */
@Entity
@Table(name = "trip_rentals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripRental extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @ElementCollection
    @CollectionTable(name = "trip_rental_dates", joinColumns = @JoinColumn(name = "trip_rental_id"))
    @Column(name = "rental_date", nullable = false)
    private Set<LocalDate> dates = new HashSet<>();

    // 픽업·반납 장소 (장소 검색 API 결과의 placeId)
    @Column(nullable = false, length = 100)
    private String placeId;

    @Column(nullable = false)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    public static TripRental of(Trip trip, Set<LocalDate> dates, String placeId, String name, String address,
                                double latitude, double longitude) {
        TripRental rental = new TripRental();
        rental.trip = trip;
        rental.dates.addAll(dates);
        rental.placeId = placeId;
        rental.name = name;
        rental.address = address;
        rental.latitude = latitude;
        rental.longitude = longitude;
        return rental;
    }
}
