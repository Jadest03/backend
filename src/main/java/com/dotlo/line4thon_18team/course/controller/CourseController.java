package com.dotlo.line4thon_18team.course.controller;

import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse;
import com.dotlo.line4thon_18team.course.dto.CourseSummaryResponse;
import com.dotlo.line4thon_18team.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 공용 코스 원본 조회. 개인별 정보가 없으므로 로그인 없이 둘러볼 수 있다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public List<CourseSummaryResponse> list(@RequestParam(required = false) String city) {
        return courseService.list(city);
    }

    @GetMapping("/{courseId}")
    public CourseDetailResponse get(@PathVariable long courseId) {
        return courseService.get(courseId);
    }
}
