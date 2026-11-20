package com.dotlo.line4thon_18team.course.dto;

import com.dotlo.line4thon_18team.course.dto.CourseSummaryResponse.Author;
import com.dotlo.line4thon_18team.course.entity.Course;
import com.dotlo.line4thon_18team.course.entity.CourseDay;
import com.dotlo.line4thon_18team.course.entity.CourseStop;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.place.TravelTime.Leg;

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

    /** 장소마다 travel을 legOf(장소)로 채운 사본 */
    public CourseDetailResponse withTravel(Function<Stop, Leg> legOf) {
        List<Day> filled = days.stream().map(day -> new Day(day.dayNumber(), day.stopCount(),
                day.stops().stream().map(stop -> stop.withTravel(legOf.apply(stop))).toList())).toList();
        return new CourseDetailResponse(id, title, country, city, dayCount, nightCount, stopCount, coverImageUrl,
                description, transportSummary, author, tags, filled);
    }

    public record Day(int dayNumber, int stopCount, List<Stop> stops) {
        static Day from(int number, CourseDay day) {
            List<Stop> stops = day == null ? List.of() : day.getStops().stream().map(Stop::from).toList();
            return new Day(number, stops.size(), stops);
        }
    }

    /** @param travel 같은 날 앞 장소에서 오는 이동 (구글 경로 조회). 그날 첫 장소는 null */
    public record Stop(Long id, int position, String placeId, String name, PlaceCategory category,
                        double latitude, double longitude, LocalTime visitTime, String imageUrl, Leg travel) {
        static Stop from(CourseStop stop) {
            return new Stop(stop.getId(), stop.getPosition(), stop.getPlaceId(), stop.getName(), stop.getCategory(),
                    stop.getLatitude(), stop.getLongitude(), stop.getVisitTime(), stop.getImageUrl(), null);
        }

        Stop withTravel(Leg leg) {
            return new Stop(id, position, placeId, name, category, latitude, longitude, visitTime, imageUrl, leg);
        }
    }
}
