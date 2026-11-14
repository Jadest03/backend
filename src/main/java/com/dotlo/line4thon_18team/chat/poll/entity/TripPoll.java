package com.dotlo.line4thon_18team.chat.poll.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 채팅방 투표 한 건 (COLLAB-POLL-001). @도틀로 투표 만들기로 생기고, 후보 중 하나를 고른다.
 *
 * <p>채팅 메시지를 가리키지 않는다. 메시지 쪽이 payload 에 pollId 를 들고 있어서 한 방향이면 충분하고,
 * 그래야 채팅 메시지 테이블(BE2)과 이 테이블을 따로 만들 수 있다.
 */
@Entity
@Table(
        name = "trip_poll",
        indexes = @Index(name = "idx_trip_poll_trip", columnList = "trip_id, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPoll extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // 화면 맨 위에 나오는 질문. 예: "2일차에 어디 갈까요?"
    @Column(nullable = false, length = 200)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PollStatus status;

    // 투표를 만든 사람 (@도틀로를 부른 사람)
    @Column(nullable = false)
    private Long createdBy;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<TripPollCandidate> candidates = new ArrayList<>();

    public static TripPoll create(Trip trip, String question, Long createdBy) {
        TripPoll poll = new TripPoll();
        poll.trip = trip;
        poll.question = question;
        poll.status = PollStatus.OPEN;
        poll.createdBy = createdBy;
        return poll;
    }

    public void addCandidate(String placeId, String name, String tagline) {
        candidates.add(TripPollCandidate.create(this, placeId, name, tagline, candidates.size()));
    }

    public void close() {
        status = PollStatus.CLOSED;
    }

    public boolean isClosed() {
        return status == PollStatus.CLOSED;
    }
}
