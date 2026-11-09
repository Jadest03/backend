package com.dotlo.line4thon_18team.course.dto;

import com.dotlo.line4thon_18team.course.entity.Course;

import java.time.YearMonth;
import java.util.List;

public record CourseSummaryResponse(Long id, String title, String country, String city,
                                     int dayCount, int nightCount, int stopCount, String coverImageUrl,
                                     String transportSummary, Author author, List<String> tags) {

    public static CourseSummaryResponse from(Course course, int stopCount) {
        return new CourseSummaryResponse(course.getId(), course.getTitle(), course.getCountry(), course.getCity(),
                course.getDayCount(), course.getNightCount(), stopCount, course.getCoverImageUrl(),
                course.getTransportSummary(), Author.from(course), course.getTags());
    }

    public record Author(String name, String imageUrl, String sourceLabel, YearMonth travelMonth) {
        static Author from(Course course) {
            return new Author(course.getAuthorName(), course.getAuthorImageUrl(), course.getSourceLabel(),
                    course.getTravelMonth());
        }
    }
}
