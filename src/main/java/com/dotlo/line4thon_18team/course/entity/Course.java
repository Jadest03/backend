package com.dotlo.line4thon_18team.course.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 직접 등록하는 인기 코스 원본. 사용자 여행(Trip)과 분리해 여러 여행에서 재사용한다. */
@Entity
@Table(name = "courses", indexes = @Index(name = "idx_courses_city_created", columnList = "city,created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 50)
    private String country;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false)
    private int dayCount;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 2000)
    private String coverImageUrl;

    // 직접 등록한 코스도 표시할 수 있도록 서비스 사용자와 FK로 묶지 않는다.
    @Column(length = 100)
    private String authorName;

    @Column(length = 2000)
    private String authorImageUrl;

    @Column(length = 100)
    private String sourceLabel;

    @Convert(converter = TravelMonthConverter.class)
    @Column(length = 7)
    private YearMonth travelMonth;

    // "도보와 버스" 같은 원본 코스의 이동 방법 소개. 구글 경로 응답을 저장하는 칸은 아니다.
    @Column(length = 100)
    private String transportSummary;

    @ElementCollection
    @CollectionTable(name = "course_tags", joinColumns = @JoinColumn(name = "course_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag", nullable = false, length = 50)
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<CourseDay> days = new ArrayList<>();

    public static Course create(String title, String country, String city, int dayCount) {
        if (title == null || title.isBlank() || country == null || country.isBlank()
                || city == null || city.isBlank() || dayCount < 1) {
            throw new IllegalArgumentException("코스 제목·나라·도시와 1일 이상의 기간이 필요합니다.");
        }
        Course course = new Course();
        course.title = title;
        course.country = country;
        course.city = city;
        course.dayCount = dayCount;
        return course;
    }

    public void describe(String description, String coverImageUrl, String transportSummary) {
        this.description = description;
        this.coverImageUrl = coverImageUrl;
        this.transportSummary = transportSummary;
    }

    public void credit(String authorName, String authorImageUrl, String sourceLabel, YearMonth travelMonth) {
        this.authorName = authorName;
        this.authorImageUrl = authorImageUrl;
        this.sourceLabel = sourceLabel;
        this.travelMonth = travelMonth;
    }

    // 코스 태그(산책·전통 등)는 장소 카테고리와 별개. 입력 순서를 유지하고 중복만 없앤다.
    public void changeTags(List<String> tags) {
        if (tags == null || tags.stream().anyMatch(tag -> tag == null || tag.isBlank() || tag.length() > 50)) {
            throw new IllegalArgumentException("코스 태그는 1~50자여야 합니다.");
        }
        this.tags.clear();
        this.tags.addAll(new LinkedHashSet<>(tags));
    }

    public CourseDay addDay(int dayNumber) {
        if (dayNumber < 1 || dayNumber > dayCount) {
            throw new IllegalArgumentException("코스 기간 안의 일차만 추가할 수 있습니다.");
        }
        if (days.stream().anyMatch(day -> day.getDayNumber() == dayNumber)) {
            throw new IllegalArgumentException("이미 등록한 일차입니다.");
        }
        CourseDay day = CourseDay.of(this, dayNumber);
        days.add(day);
        return day;
    }

    public List<CourseDay> getDays() {
        return days.stream().sorted(java.util.Comparator.comparingInt(CourseDay::getDayNumber)).toList();
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public int getNightCount() {
        return dayCount - 1;
    }

    /** 방문 항목 수. 같은 장소를 다른 일차에 다시 방문하면 각각 센다. */
    public int getStopCount() {
        return days.stream().mapToInt(day -> day.getStops().size()).sum();
    }
}
