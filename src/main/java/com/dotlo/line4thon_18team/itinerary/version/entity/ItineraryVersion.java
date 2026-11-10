package com.dotlo.line4thon_18team.itinerary.version.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정 한 버전. 일정을 고칠 때마다 지우지 않고 새 버전을 쌓는다 (되돌리기, 변경점 비교용).
 * 같은 여행에서 버전 번호는 겹치지 않는다 (동시에 저장해도 DB가 막는다).
 */
@Entity
@Table(
        name = "itinerary_version",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_itinerary_version_trip_version",
                columnNames = {"trip_id", "version"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private int version;

    // 어느 버전에서 파생됐는지. AI 생성은 null
    private Integer baseVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private ItinerarySource source;

    @Column(nullable = false)
    private Long createdBy;

    // 일차별 방문 순서 (ItineraryDraft JSON). 시각은 없다
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    // 검증 결과 (ValidationResult JSON). 화면에 그릴 시각은 여기 days 에 있다
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String validation;

    public static ItineraryVersion create(Trip trip, int version, Integer baseVersion, ItinerarySource source,
                                          Long createdBy, String content, String validation) {
        ItineraryVersion itinerary = new ItineraryVersion();
        itinerary.trip = trip;
        itinerary.version = version;
        itinerary.baseVersion = baseVersion;
        itinerary.source = source;
        itinerary.createdBy = createdBy;
        itinerary.content = content;
        itinerary.validation = validation;
        return itinerary;
    }
}
