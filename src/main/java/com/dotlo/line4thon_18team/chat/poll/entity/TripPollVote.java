package com.dotlo.line4thon_18team.chat.poll.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 사람의 한 표. 다시 투표하면 이 줄의 후보만 바뀐다.
 * (poll_id, user_id) 유니크라 같은 요청이 동시에 두 번 들어와도 두 줄이 생기지 않는다.
 */
@Entity
@Table(
        name = "trip_poll_vote",
        uniqueConstraints = @UniqueConstraint(name = "uk_trip_poll_vote", columnNames = {"poll_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPollVote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private TripPoll poll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private TripPollCandidate candidate;

    @Column(nullable = false)
    private Long userId;

    public static TripPollVote create(TripPoll poll, TripPollCandidate candidate, Long userId) {
        TripPollVote vote = new TripPollVote();
        vote.poll = poll;
        vote.candidate = candidate;
        vote.userId = userId;
        return vote;
    }

    public void changeTo(TripPollCandidate candidate) {
        this.candidate = candidate;
    }
}
