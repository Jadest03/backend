package com.dotlo.line4thon_18team.course.dto;

import com.dotlo.line4thon_18team.course.dto.CourseSummaryResponse.Author;
import com.dotlo.line4thon_18team.course.entity.Course;
import com.dotlo.line4thon_18team.course.entity.CourseDay;
import com.dotlo.line4thon_18team.course.entity.CourseStop;
import com.dotlo.line4thon_18team.place.PlaceCategory;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record CourseDetailResponse(Long id, String title, String country, String city,
                                    int dayCount, int nightCount, int stopCount, String coverImageUrl,
                                    String description, String transportSummary, Author author, List<String> tags,
                                    List<Day> days) {

    public static CourseDetailResponse from(Course course, List<CourseDay> savedDays) {
        Map<Integer, CourseDay> byNumber = savedDays.stream()
                .collect(Collectors.toMap(CourseDay::getDayNumber, Function.identity()));
        List<Day> days = IntStream.rangeClosed(1, course.getDayCount())
                .mapToObj(number -> Day.from(number, byNumber.get(number))).toList();
        int count = days.stream().mapToInt(Day::stopCount).sum();
        return new CourseDetailResponse(course.getId(), course.getTitle(), course.getCountry(), course.getCity(),
                course.getDayCount(), course.getNightCount(), count, course.getCoverImageUrl(),
                course.getDescription(), course.getTransportSummary(), Author.from(course), course.getTags(), days);
    }

    public record Day(int dayNumber, int stopCount, List<Stop> stops) {
        static Day from(int number, CourseDay day) {
            List<Stop> stops = day == null ? List.of() : day.getStops().stream().map(Stop::from).toList();
            return new Day(number, stops.size(), stops);
        }
    }

    public record Stop(Long id, int position, String placeId, String name, PlaceCategory category,
                        double latitude, double longitude, LocalTime visitTime, String imageUrl) {
        static Stop from(CourseStop stop) {
            return new Stop(stop.getId(), stop.getPosition(), stop.getPlaceId(), stop.getName(), stop.getCategory(),
                    stop.getLatitude(), stop.getLongitude(), stop.getVisitTime(), stop.getImageUrl());
        }
    }
}
