package com.dotlo.line4thon_18team.course.service;

import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse;
import com.dotlo.line4thon_18team.course.dto.CourseSummaryResponse;
import com.dotlo.line4thon_18team.course.entity.Course;
import com.dotlo.line4thon_18team.course.repository.CourseDayRepository;
import com.dotlo.line4thon_18team.course.repository.CourseRepository;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseDayRepository dayRepository;

    public List<CourseSummaryResponse> list(String city) {
        if (city != null) {
            city = city.strip();
            if (city.isEmpty() || city.length() > 100) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "city는 1~100자로 입력해 주세요.");
            }
        }
        List<Course> courses = city == null
                ? courseRepository.findAllByOrderByCreatedAtDescIdDesc()
                : courseRepository.findAllByCityOrderByCreatedAtDescIdDesc(city);
        if (courses.isEmpty()) return List.of();

        Map<Long, Integer> counts = courseRepository.countStopsByCourseIds(courses.stream().map(Course::getId).toList())
                .stream().collect(Collectors.toMap(CourseRepository.StopCount::getCourseId,
                        row -> Math.toIntExact(row.getStopCount())));
        return courses.stream().map(course -> CourseSummaryResponse.from(course, counts.getOrDefault(course.getId(), 0)))
                .toList();
    }

    // 코스에 없는 장소(또는 없는 코스)는 사진도 없다고 본다
    public void requireStop(long courseId, String placeId) {
        if (!courseRepository.hasStop(courseId, placeId)) throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
    }

    public CourseDetailResponse get(long courseId) {
        if (courseId < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "courseId는 양수여야 합니다.");
        }
        Course course = courseRepository.findWithTagsById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        return CourseDetailResponse.from(course, dayRepository.findAllWithStopsByCourseId(courseId));
    }
}
