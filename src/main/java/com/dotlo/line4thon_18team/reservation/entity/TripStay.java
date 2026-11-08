package com.dotlo.line4thon_18team.reservation.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 여행의 숙소 1곳 (BE2). 밤마다 다른 숙소일 수 있어서 여행 1개에 여러 행.
 * checkIn 날 밤부터 checkOut 전날 밤까지 묵는다. 예) 5/18~5/20 = 18·19일 밤 (2박)
 */
@Entity
@Table(name = "trip_stays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripStay extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // 구글 장소 (장소 검색 API 결과의 placeId)
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

    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut;

    public static TripStay of(Trip trip, String placeId, String name, String address, double latitude, double longitude,
                              LocalDate checkIn, LocalDate checkOut) {
        TripStay stay = new TripStay();
        stay.trip = trip;
        stay.placeId = placeId;
        stay.name = name;
        stay.address = address;
        stay.latitude = latitude;
        stay.longitude = longitude;
        stay.checkIn = checkIn;
        stay.checkOut = checkOut;
        return stay;
    }
}
