package com.dotlo.line4thon_18team.chat.poll.repository;

import com.dotlo.line4thon_18team.chat.poll.entity.TripPollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripPollVoteRepository extends JpaRepository<TripPollVote, Long> {

    Optional<TripPollVote> findByPollIdAndUserId(Long pollId, Long userId);

    List<TripPollVote> findByPollIdOrderByIdAsc(Long pollId);

    /** 투표 여러 건의 표를 한 번에. 채팅 조회가 최근 투표를 함께 내려줄 때 쓴다. */
    List<TripPollVote> findByPollIdInOrderByIdAsc(Collection<Long> pollIds);

    void deleteByPollId(Long pollId);

    /** 여행 삭제용. 투표보다 먼저 지워야 한다 (poll_id, candidate_id 외래키). */
    void deleteByPoll_Trip_Id(Long tripId);
}
