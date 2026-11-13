package com.dotlo.line4thon_18team.reservation;

import com.dotlo.line4thon_18team.reservation.dto.FlightInfo;
import com.dotlo.line4thon_18team.reservation.dto.FlightInfo.Point;
import com.dotlo.line4thon_18team.reservation.entity.FlightDirection;
import com.dotlo.line4thon_18team.reservation.entity.TripFlight;
import com.dotlo.line4thon_18team.reservation.entity.TripRental;
import com.dotlo.line4thon_18team.reservation.entity.TripStay;
import com.dotlo.line4thon_18team.reservation.repository.TripFlightRepository;
import com.dotlo.line4thon_18team.reservation.repository.TripRentalRepository;
import com.dotlo.line4thon_18team.reservation.repository.TripStayRepository;
import com.dotlo.line4thon_18team.reservation.service.ReservationTripCleanupService;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행 삭제용 BE2 예약 정보 정리. MySQL이 필요하다 (docker compose up -d).
 * 정리가 자기 트랜잭션을 열어서 테스트 트랜잭션으로 되돌릴 수 없으므로, 만든 데이터는 테스트 끝에 지운다.
 */
@SpringBootTest
class ReservationTripCleanupServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripFlightRepository flights;
    @Autowired TripStayRepository stays;
    @Autowired TripRentalRepository rentals;
    @Autowired ReservationTripCleanupService cleanupService;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManager em;

    private User owner;
    private Trip target;
    private Trip other;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser());
        target = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.ALONE, null));
        other = trips.save(Trip.create(owner, "교토", START, START.plusDays(2), Companion.ALONE, null));
        fill(target);
        fill(other);
    }

    @AfterEach
    void tearDown() {
        cleanupService.deleteByTrip(target.getId());
        cleanupService.deleteByTrip(other.getId());
        trips.findById(target.getId()).ifPresent(trips::delete);
        trips.delete(other);
        users.delete(owner);
    }

    @Test
    void 그_여행의_항공_숙소_렌터카와_렌터카_날짜를_모두_지운다() {
        long rentalId = rentals.findByTripId(target.getId()).orElseThrow().getId();
        assertThat(rentalDateRows(rentalId)).isEqualTo(2);

        cleanupService.deleteByTrip(target.getId());

        assertThat(flights.findAllByTripId(target.getId())).isEmpty();
        assertThat(stays.findAllByTripIdOrderByCheckInAsc(target.getId())).isEmpty();
        assertThat(rentals.findByTripId(target.getId())).isEmpty();
        assertThat(rentalDateRows(rentalId)).isZero();   // 렌터카 쓰는 날도 같이 지워짐
    }

    @Test
    void 다른_여행의_예약_정보는_건드리지_않는다() {
        cleanupService.deleteByTrip(target.getId());

        assertThat(flights.findAllByTripId(other.getId())).hasSize(2);
        assertThat(stays.findAllByTripIdOrderByCheckInAsc(other.getId())).hasSize(2);
        assertThat(rentals.findByTripId(other.getId())).get()
                .satisfies(rental -> assertThat(rental.getDates()).hasSize(2));
    }

    @Test
    void 같은_트랜잭션에서_정리한_뒤_여행을_지울_수_있다() {
        tx.executeWithoutResult(status -> {
            Trip trip = trips.findById(target.getId()).orElseThrow();
            cleanupService.deleteByTrip(trip.getId());
            trips.delete(trip);
            trips.flush();
        });

        assertThat(trips.findById(target.getId())).isEmpty();
    }

    @Test
    void 지울_것이_없어도_그냥_끝난다() {
        cleanupService.deleteByTrip(target.getId());

        cleanupService.deleteByTrip(target.getId());   // 두 번째 호출
        assertThat(flights.findAllByTripId(target.getId())).isEmpty();
    }

    /** 항공 2개(가는 편·오는 편) + 숙소 2개 + 렌터카 1개(쓰는 날 2일) */
    private void fill(Trip trip) {
        flights.save(TripFlight.of(trip, FlightDirection.OUTBOUND, flight("KE723", START)));
        flights.save(TripFlight.of(trip, FlightDirection.RETURN, flight("KE724", START.plusDays(2))));
        stays.save(TripStay.of(trip, "stay-1", "숙소1", "주소1", 34.67, 135.50, START, START.plusDays(1)));
        stays.save(TripStay.of(trip, "stay-2", "숙소2", "주소2", 34.68, 135.51, START.plusDays(1), START.plusDays(2)));
        rentals.save(TripRental.of(trip, Set.of(START, START.plusDays(1)), "rent-1", "간사이 공항", "공항 주소",
                34.43, 135.23));
    }

    private static FlightInfo flight(String number, LocalDate date) {
        return new FlightInfo(number, "Korean Air",
                new Point("ICN", LocalDateTime.of(date, LocalTime.of(9, 30)), 37.46, 126.44),
                new Point("KIX", LocalDateTime.of(date, LocalTime.of(11, 20)), 34.43, 135.23));
    }

    private int rentalDateRows(long rentalId) {
        return tx.execute(status -> ((Number) em.createNativeQuery(
                        "select count(*) from trip_rental_dates where trip_rental_id = :id")
                .setParameter("id", rentalId).getSingleResult()).intValue());
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-rsv-clean-" + id).email(id + "@test.com").name("예약정리").build();
    }
}
