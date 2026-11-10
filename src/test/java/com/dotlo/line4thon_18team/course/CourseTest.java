package com.dotlo.line4thon_18team.course;

import com.dotlo.line4thon_18team.course.entity.Course;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 기간은_1일_이상이어야_한다(int dayCount) {
        assertThatIllegalArgumentException().isThrownBy(() -> Course.create("교토 산책", "일본", "교토", dayCount));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 4})
    void 기간_밖_일차는_추가하지_못한다(int dayNumber) {
        Course course = Course.create("교토 산책", "일본", "교토", 3);

        assertThatIllegalArgumentException().isThrownBy(() -> course.addDay(dayNumber));
        assertThat(course.getDays()).isEmpty();
    }

    @Test
    void 같은_일차는_중복으로_추가하지_못한다() {
        Course course = Course.create("교토 산책", "일본", "교토", 3);
        course.addDay(1);

        assertThatIllegalArgumentException().isThrownBy(() -> course.addDay(1));
        assertThat(course.getDays()).hasSize(1);
    }

    @Test
    void 잘못된_좌표의_장소는_추가하지_못한다() {
        var day = Course.create("교토 산책", "일본", "교토", 1).addDay(1);

        assertThatIllegalArgumentException().isThrownBy(() ->
                day.addStop("test-place", "장소", Double.NaN, 135.78, PlaceCategory.CULTURE, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                day.addStop("test-place", "장소", 34.99, 181, PlaceCategory.CULTURE, null));
        assertThat(day.getStops()).isEmpty();
    }

    @Test
    void 외부에서_목록을_바꿔_일차와_순서_검사를_우회하지_못한다() {
        Course course = Course.create("교토 산책", "일본", "교토", 1);
        var day = course.addDay(1);
        day.addStop("test-place", "장소", 34.99, 135.78, PlaceCategory.CULTURE, null);
        course.changeTags(List.of("산책"));

        assertThatThrownBy(() -> course.getDays().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> day.getStops().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> course.getTags().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 잘못된_태그로_바꾸면_기존_태그는_남는다() {
        Course course = Course.create("교토 산책", "일본", "교토", 1);
        course.changeTags(List.of("산책"));

        assertThatIllegalArgumentException().isThrownBy(() -> course.changeTags(List.of("전통", " ")));
        assertThat(course.getTags()).containsExactly("산책");
    }
}
