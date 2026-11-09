package com.dotlo.line4thon_18team.course.entity;

import com.dotlo.line4thon_18team.place.PlaceCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/** 일차별 방문 장소. 여행에 종속된 SavedPlace와 FK로 묶지 않는다. */
@Entity
@Table(name = "course_stops", uniqueConstraints = @UniqueConstraint(
        name = "uk_course_stop_position", columnNames = {"day_id", "stop_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private CourseDay day;

    @Column(name = "stop_order", nullable = false)
    private int position;

    @Column(nullable = false, length = 100)
    private String placeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PlaceCategory category;

    private LocalTime visitTime;

    @Column(length = 2000)
    private String imageUrl;

    static CourseStop of(CourseDay day, int position, String placeId, String name, double latitude, double longitude,
                          PlaceCategory category, LocalTime visitTime) {
        if (placeId == null || placeId.isBlank() || name == null || name.isBlank() || category == null) {
            throw new IllegalArgumentException("구글 장소 id·이름·카테고리가 필요합니다.");
        }
        if (!(latitude >= -90 && latitude <= 90) || !(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("장소 좌표가 올바르지 않습니다.");
        }
        CourseStop stop = new CourseStop();
        stop.day = day;
        stop.position = position;
        stop.placeId = placeId;
        stop.name = name;
        stop.latitude = latitude;
        stop.longitude = longitude;
        stop.category = category;
        stop.visitTime = visitTime;
        return stop;
    }

    public void changeImage(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
