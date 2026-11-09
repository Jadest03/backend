package com.dotlo.line4thon_18team.course.entity;

import com.dotlo.line4thon_18team.place.PlaceCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 원본 코스의 N일차. 실제 여행 날짜는 코스를 사용자 여행에 담을 때 정한다. */
@Entity
@Table(name = "course_days", uniqueConstraints = @UniqueConstraint(
        name = "uk_course_day_number", columnNames = {"course_id", "day_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CourseStop> stops = new ArrayList<>();

    static CourseDay of(Course course, int dayNumber) {
        CourseDay day = new CourseDay();
        day.course = course;
        day.dayNumber = dayNumber;
        return day;
    }

    /** 전달한 순서대로 방문한다. 방문 시각은 원본 코스의 예시이며 없어도 된다. */
    public CourseStop addStop(String placeId, String name, double latitude, double longitude,
                              PlaceCategory category, LocalTime visitTime) {
        CourseStop stop = CourseStop.of(this, stops.size() + 1, placeId, name, latitude, longitude, category, visitTime);
        stops.add(stop);
        return stop;
    }

    public List<CourseStop> getStops() {
        return List.copyOf(stops);
    }
}
