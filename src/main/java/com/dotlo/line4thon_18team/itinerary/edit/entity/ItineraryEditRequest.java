package com.dotlo.line4thon_18team.itinerary.edit.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI가 만든 수정안 한 건 (EDIT-TEXT-001). 사용자가 적용을 누를 때까지 여기에 두고,
 * 적용하면 그때 일정 버전(itinerary_version)으로 저장한다.
 *
 * <p>일정 버전으로 바로 쌓지 않는 이유는 두 가지다. 최신 일정 조회가 버전 번호가 가장 큰 것을
 * 돌려주므로 적용하지 않은 수정안이 "현재 일정"이 되고, 저장할 때 고치기 전 버전이 최신이어야
 * 하므로 같은 일정에 수정 요청을 두 번 연달아 하면 두 번째가 버전 충돌로 막힌다.
 * 변경 이력과 되돌리기 목록에 쓰지 않은 수정안이 섞이는 것도 피한다.
 *
 * <p>같은 버전을 기준으로 여러 번 요청할 수 있어서 한 (여행, 기준 버전)에 여러 행이 쌓인다.
 * 적용할 때는 가장 나중 것을 쓴다.
 */
@Entity
@Table(
        name = "itinerary_edit_request",
        indexes = @Index(
                name = "idx_itinerary_edit_request_trip_base",
                columnList = "trip_id, base_version"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryEditRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // 이 수정안이 고친 버전. 적용할 때 이 번호가 여전히 최신이어야 한다 (아니면 409)
    @Column(nullable = false)
    private int baseVersion;

    // 사용자가 적은 수정 요청. 어떤 말에 어떤 수정안이 나왔는지 보려고 남긴다
    @Column(nullable = false, length = 500)
    private String message;

    // AI가 쓴 "무엇을 어떻게 고쳤는지". 화면에 그대로 보여준다
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    // 고친 일차별 방문 순서 (ItineraryDraft JSON). 시각은 없다
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    // 검증 결과 (ValidationResult JSON). 적용할 때 다시 검증하지 않고 이 값을 그대로 저장한다
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String validation;

    @Column(nullable = false)
    private Long createdBy;

    public static ItineraryEditRequest create(Trip trip, int baseVersion, String message, String reason,
                                              Long createdBy, String content, String validation) {
        ItineraryEditRequest edit = new ItineraryEditRequest();
        edit.trip = trip;
        edit.baseVersion = baseVersion;
        edit.message = message;
        edit.reason = reason;
        edit.createdBy = createdBy;
        edit.content = content;
        edit.validation = validation;
        return edit;
    }
}
