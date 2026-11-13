package com.dotlo.line4thon_18team.course.controller;

import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse;
import com.dotlo.line4thon_18team.course.dto.CourseSummaryResponse;
import com.dotlo.line4thon_18team.course.service.CourseService;
import com.dotlo.line4thon_18team.place.PlacePhotos;
import com.dotlo.line4thon_18team.place.PlacePhotos.Photo;
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
    private final PlacePhotos placePhotos;

    @GetMapping
    public List<CourseSummaryResponse> list(@RequestParam(required = false) String city) {
        return courseService.list(city);
    }

    @GetMapping("/{courseId}")
    public CourseDetailResponse get(@PathVariable long courseId) {
        return courseService.get(courseId);
    }

    // 비회원도 코스 상세를 보므로 로그인 없이 연다. 대신 이 코스에 들어 있는 장소 사진만 준다 (아무 장소나 불러 요금을 쓰지 못하게)
    @GetMapping("/{courseId}/photos/{placeId}/{index}")
    public Photo photo(@PathVariable long courseId, @PathVariable String placeId, @PathVariable int index,
                       @RequestParam(defaultValue = "400") int maxWidth) {
        courseService.requireStop(courseId, placeId);
        return placePhotos.get(placeId, index, PlacePhotos.width(maxWidth));
    }
}
