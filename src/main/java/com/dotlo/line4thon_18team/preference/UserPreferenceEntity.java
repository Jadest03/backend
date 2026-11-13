package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.preference.domain.DayStartPreference;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.preference.domain.TravelPace;
import com.dotlo.line4thon_18team.preference.domain.WalkingPreference;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 사용자 기본 취향 테이블. 사용자마다 한 줄.
 *
 * <p>취향 칸은 {@link PreferenceEntity}(여행별 취향)와 같은 모양이다. 같은 도메인의 두 테이블을
 * 다른 방식으로 저장하면 읽기 어렵고, 문자열 목록 변환기도 이미 있다.
 *
 * <p>여행별 취향과 달리 user_id 에 외래키를 건다. trip_preferences 는 FK가 없어서 여행을 지울 때
 * 고아 행이 조용히 남는데(제약이 막아주지 않는다), 새로 만드는 쪽은 회원을 지울 때 제약이
 * 걸리도록 둔다.
 *
 * <p>updatedAt 은 응답에 서비스 시계(Clock) 기준 시각을 그대로 쓰려고 직접 둔다.
 */
@Entity
@Table(name = "user_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private TravelPace pace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private WalkingPreference walking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private DayStartPreference dayStart;

    // 장소 유형 이름 목록. 예) ["CAFE","FOOD"]
    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, length = 200)
    private List<String> interests;

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, length = 200)
    private List<String> avoid;

    // 최대 5개, 각 100자 (PreferenceRules)
    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, length = 1000)
    private List<String> notes;

    // 사용자가 입력한 취향 원문 (최대 500자)
    @Column(length = 500)
    private String rawText;

    @Column(nullable = false)
    private Instant updatedAt;

    UserPreferenceEntity(User user) {
        this.user = user;
    }

    void update(PreferenceProfile profile, String rawText, Instant updatedAt) {
        this.pace = profile.pace();
        this.walking = profile.walking();
        this.dayStart = profile.dayStart();
        this.interests = profile.interests().stream().map(Enum::name).toList();
        this.avoid = profile.avoid().stream().map(Enum::name).toList();
        this.notes = profile.notes();
        this.rawText = rawText;
        this.updatedAt = updatedAt;
    }

    StoredUserPreference toStored() {
        PreferenceProfile profile = new PreferenceProfile(pace, walking, dayStart,
                categories(interests), categories(avoid), notes);
        return new StoredUserPreference(user.getId(), profile, rawText, updatedAt);
    }

    private static List<PlaceCategory> categories(List<String> names) {
        return names.stream().map(PlaceCategory::valueOf).toList();
    }
}
