package com.dotlo.line4thon_18team.trip.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 여행 1건. 저장 장소·취향·일정이 이 여행에 붙는다.
 * 날짜 순서, 동행 인원 규칙은 생성·수정할 때 여기서 검사한다.
 */
@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 지원 도시 목록(GET /api/cities)의 name
    @Column(nullable = false, length = 50)
    private String city;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // enum 은 MySQL ENUM 대신 varchar 로 저장 (값이 추가돼도 컬럼을 고칠 필요가 없게)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Companion companion;

    // 나를 포함한 인원
    @Column(nullable = false)
    private int companionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private TripStatus status;

    public static Trip create(User user, String city, LocalDate startDate, LocalDate endDate,
                              Companion companion, Integer companionCount) {
        Trip trip = new Trip();
        trip.user = user;
        trip.status = TripStatus.DRAFT;
        trip.apply(city, startDate, endDate, companion, companionCount);
        return trip;
    }

    // 보낸 값만 바꾼다 (null 은 그대로 유지)
    public void update(String city, LocalDate startDate, LocalDate endDate,
                       Companion companion, Integer companionCount) {
        apply(
                city != null ? city : this.city,
                startDate != null ? startDate : this.startDate,
                endDate != null ? endDate : this.endDate,
                companion != null ? companion : this.companion,
                // 동행 종류만 바꾸면 이전 인원은 의미가 없어서 새로 정한다
                companionCount != null ? companionCount : (companion == null ? this.companionCount : null)
        );
    }

    public void confirm() {
        this.status = TripStatus.CONFIRMED;
    }

    // 방장이 나가거나 탈퇴하면 다음 멤버가 방장이 된다
    public void changeOwner(User owner) {
        this.user = owner;
    }

    private void apply(String city, LocalDate startDate, LocalDate endDate,
                       Companion companion, Integer companionCount) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        this.city = city;
        this.startDate = startDate;
        this.endDate = endDate;
        this.companion = companion;
        this.companionCount = resolveCount(companion, companionCount);
    }

    // 혼자/연인은 인원이 정해져 있어서 자동으로 넣는다
    // 친구/가족은 생성 화면에서 인원을 받지 않아서 없으면 최소 인원, 보냈으면 범위 안인지 확인
    private static int resolveCount(Companion companion, Integer count) {
        if (companion.isFixedCount() || count == null) {
            return companion.getMinCount();
        }
        if (!companion.allows(count)) {
            throw new BusinessException(ErrorCode.INVALID_COMPANION_COUNT);
        }
        return count;
    }
}
