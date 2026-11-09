package com.dotlo.line4thon_18team.course.repository;

import com.dotlo.line4thon_18team.course.entity.Course;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    @EntityGraph(attributePaths = "tags")
    List<Course> findAllByCityOrderByCreatedAtDescIdDesc(String city);

    @EntityGraph(attributePaths = "tags")
    List<Course> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = "tags")
    Optional<Course> findWithTagsById(Long id);

    // 목록에서는 일차·장소 전체를 읽지 않고 방문 항목 수만 한 번에 센다.
    @Query("""
            select stop.day.course.id as courseId, count(stop) as stopCount
            from CourseStop stop
            where stop.day.course.id in :courseIds
            group by stop.day.course.id
            """)
    List<StopCount> countStopsByCourseIds(@Param("courseIds") List<Long> courseIds);

    interface StopCount {
        Long getCourseId();
        long getStopCount();
    }
}
