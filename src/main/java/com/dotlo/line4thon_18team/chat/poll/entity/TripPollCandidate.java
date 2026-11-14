package com.dotlo.line4thon_18team.chat.poll.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투표 후보 한 곳.
 *
 * <p>저장 장소에 미리 담지 않는다. 1위를 일정에 넣을 때 장소 넣기 API 가 담는다
 * (거기서 구글 조회가 한 번 일어나는데, 뽑히지 않은 후보까지 미리 담을 이유가 없다).
 */
@Entity
@Table(
        name = "trip_poll_candidate",
        indexes = @Index(name = "idx_trip_poll_candidate_poll", columnList = "poll_id, sort_order")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPollCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private TripPoll poll;

    @Column(nullable = false, length = 100)
    private String placeId;

    @Column(nullable = false, length = 200)
    private String name;

    // 이름 밑에 붙는 짧은 설명. 지금은 장소 유형("공원", "수족관")을 쓴다
    @Column(length = 100)
    private String tagline;

    @Column(nullable = false)
    private int sortOrder;

    static TripPollCandidate create(TripPoll poll, String placeId, String name, String tagline, int sortOrder) {
        TripPollCandidate candidate = new TripPollCandidate();
        candidate.poll = poll;
        candidate.placeId = placeId;
        candidate.name = name;
        candidate.tagline = tagline;
        candidate.sortOrder = sortOrder;
        return candidate;
    }
}
