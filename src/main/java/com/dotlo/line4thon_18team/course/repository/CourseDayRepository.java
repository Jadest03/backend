package com.dotlo.line4thon_18team.course.repository;

import com.dotlo.line4thon_18team.course.entity.CourseDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseDayRepository extends JpaRepository<CourseDay, Long> {

    // 코스·일차·장소 컬렉션을 한꺼번에 조인하지 않고, 일차와 장소를 함께 읽는다.
    @Query("""
            select distinct day from CourseDay day
            left join fetch day.stops
            where day.course.id = :courseId
            order by day.dayNumber
            """)
    List<CourseDay> findAllWithStopsByCourseId(@Param("courseId") Long courseId);
}
