package com.dotlo.line4thon_18team.chat.poll.repository;

import com.dotlo.line4thon_18team.chat.poll.entity.TripPoll;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripPollRepository extends JpaRepository<TripPoll, Long> {

    Optional<TripPoll> findByIdAndTripId(Long id, Long tripId);

    /** 채팅 조회에 같이 실을 최근 투표. 오래된 투표까지 매번 내려줄 필요는 없다. */
    List<TripPoll> findByTripIdOrderByIdDesc(Long tripId, Limit limit);

    /** 여행 삭제용. */
    void deleteByTripId(Long tripId);
}
