package com.dotlo.line4thon_18team.course.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse;
import com.dotlo.line4thon_18team.course.dto.CourseImportRequest;
import com.dotlo.line4thon_18team.course.dto.CourseImportResponse;
import com.dotlo.line4thon_18team.course.dto.CourseSummaryResponse;
import com.dotlo.line4thon_18team.course.service.CourseImportService;
import com.dotlo.line4thon_18team.course.service.CourseService;
import com.dotlo.line4thon_18team.course.service.CourseTravel;
import com.dotlo.line4thon_18team.place.PlacePhotos;
import com.dotlo.line4thon_18team.place.PlacePhotos.Photo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** 공용 코스 원본 조회는 개인별 정보가 없으므로 로그인 없이 둘러볼 수 있다. 가져오기와 찜은 로그인이 필요하다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final CourseImportService courseImportService;
    private final PlacePhotos placePhotos;
    private final CourseTravel courseTravel;

    @GetMapping
    public List<CourseSummaryResponse> list(@RequestParam(required = false) String city) {
        return courseService.list(city);
    }

    // 구간 이동은 DB 조회가 끝난 뒤 구한다 (구글을 기다리는 동안 DB 연결을 잡지 않게)
    @GetMapping("/{courseId}")
    public CourseDetailResponse get(@PathVariable long courseId) {
        return courseTravel.attach(courseService.get(courseId));
    }

    // 비회원도 코스 상세를 보므로 로그인 없이 연다. 대신 이 코스에 들어 있는 장소 사진만 준다 (아무 장소나 불러 요금을 쓰지 못하게)
    @GetMapping("/{courseId}/photos/{placeId}/{index}")
    public Photo photo(@PathVariable long courseId, @PathVariable String placeId, @PathVariable int index,
                       @RequestParam(defaultValue = "400") int maxWidth) {
        courseService.requireStop(courseId, placeId);
        return placePhotos.get(placeId, index, PlacePhotos.width(maxWidth));
    }

    /**
     * 내 일정으로 가져오기 → 201. 코스 기간만큼 새 여행을 만들고 코스 일정을 그대로 넣는다.
     * 일정 검증(영업시간·이동시간 조회)이 들어가서 몇 초 걸릴 수 있다.
     */
    @PostMapping("/{courseId}/import")
    public ResponseEntity<CourseImportResponse> importCourse(@LoginUser Long userId, @PathVariable long courseId,
                                                             @Valid @RequestBody CourseImportRequest request) {
        CourseImportResponse response = courseImportService.importAsNewTrip(userId, courseId, request);
        return ResponseEntity.created(URI.create("/api/trips/" + response.tripId())).body(response);
    }

    // 내가 찜한 코스 (최근에 찜한 순). 코스 목록과 같은 모양
    @GetMapping("/bookmarks")
    public List<CourseSummaryResponse> bookmarks(@LoginUser Long userId) {
        return courseService.bookmarks(userId);
    }

    // 코스 찜 → 204. 이미 찜했어도 204, 없는 코스는 404
    @PostMapping("/{courseId}/bookmark")
    public ResponseEntity<Void> bookmark(@LoginUser Long userId, @PathVariable long courseId) {
        courseService.bookmark(userId, courseId);
        return ResponseEntity.noContent().build();
    }

    // 찜 해제 → 204. 찜하지 않았어도 204
    @DeleteMapping("/{courseId}/bookmark")
    public ResponseEntity<Void> unbookmark(@LoginUser Long userId, @PathVariable long courseId) {
        courseService.unbookmark(userId, courseId);
        return ResponseEntity.noContent().build();
    }
}
