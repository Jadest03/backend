package com.dotlo.line4thon_18team.reservation.service;

import com.dotlo.line4thon_18team.reservation.repository.TripFlightRepository;
import com.dotlo.line4thon_18team.reservation.repository.TripRentalRepository;
import com.dotlo.line4thon_18team.reservation.repository.TripStayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행을 지울 때 BE2 예약 정보(항공·숙소·렌터카)를 정리한다. 여행 삭제에서 이 메서드 하나만 부르면 된다.
 *
 * <p>trip_flights, trip_stays, trip_rentals 모두 trip_id 외래키라 안 지우면 여행 삭제가 실패한다.
 * 렌터카 쓰는 날(trip_rental_dates)은 렌터카를 지울 때 같이 지워진다.
 *
 * <p>BE3 정리(ItineraryTripCleanupService)와 같은 이유로 벌크 delete 대신 파생 delete 를 쓴다.
 * 부르는 쪽이 들고 있는 Trip 이 영속성 컨텍스트에서 떨어지지 않아 바로 뒤의 여행 삭제가 그대로 된다.
 *
 * <p>권한 확인은 하지 않는다. 부르는 쪽(여행 삭제)이 이미 내 여행인지 보고 들어온다.
 */
@Service
@RequiredArgsConstructor
public class ReservationTripCleanupService {

    private final TripFlightRepository flightRepository;
    private final TripStayRepository stayRepository;
    private final TripRentalRepository rentalRepository;

    @Transactional
    public void deleteByTrip(long tripId) {
        flightRepository.deleteAllByTripId(tripId);
        stayRepository.deleteAllByTripId(tripId);
        rentalRepository.deleteByTripId(tripId);
    }
}
