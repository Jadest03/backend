package com.dotlo.line4thon_18team.course.service;

import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse;
import com.dotlo.line4thon_18team.course.entity.Course;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.place.TravelTime;
import com.dotlo.line4thon_18team.place.TravelTime.Leg;
import com.dotlo.line4thon_18team.place.TravelTime.Mode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 구글 대신 직선거리 추정으로 넘어가는 경우 (하루 상한, 제한 시간). 구글은 부르지 않는다. */
class CourseTravelTest {

    private static final Leg GOOGLE = new Leg(12, Mode.TRANSIT, false, 1800);

    private final TravelTime travelTime = mock(TravelTime.class);

    @Test
    void 하루_구간_상한을_넘기는_코스는_구글_없이_추정한다() {
        when(travelTime.get(any(), any(), any())).thenReturn(GOOGLE);
        CourseTravel travel = new CourseTravel(travelTime, Clock.systemUTC(), Runnable::run, 3, Duration.ofSeconds(3));

        var first = travel.attach(course());   // 구간 2개 → 상한 3 안
        var second = travel.attach(course());  // 2개 더면 4 → 넘음

        assertThat(first.days().get(0).stops().get(1).travel()).isEqualTo(GOOGLE);
        assertThat(second.days().get(0).stops().get(1).travel().estimated()).isTrue();
        assertThat(second.days().get(0).stops().get(2).travel().estimated()).isTrue();
        verify(travelTime, times(2)).get(any(), any(), any());
    }

    @Test
    void 제한_시간_안에_못_받은_구간은_추정한다() {
        when(travelTime.get(any(), any(), any())).thenAnswer(inv -> {
            Thread.sleep(1000);
            return GOOGLE;
        });
        CourseTravel travel = new CourseTravel(travelTime, Clock.systemUTC(), Executors.newCachedThreadPool(),
                100, Duration.ofMillis(50));

        var result = travel.attach(course());

        assertThat(result.days().get(0).stops().get(0).travel()).isNull();
        assertThat(result.days().get(0).stops().get(1).travel().estimated()).isTrue();
        assertThat(result.days().get(0).stops().get(1).travel().mode()).isEqualTo(Mode.WALK);  // 약 1.3km → 걸어서 20분 이내
    }

    // 오사카 장소 3개 (구간 2개). DB에 넣지 않아 id는 null
    private static CourseDetailResponse course() {
        Course course = Course.create("오사카 코스", "일본", "오사카", 1);
        var day = course.addDay(1);
        day.addStop("a", "난바", 34.6687, 135.5013, PlaceCategory.SIGHTSEEING, null);
        day.addStop("b", "도톤보리", 34.6688, 135.5030, PlaceCategory.FOOD, null);
        day.addStop("c", "신사이바시", 34.6750, 135.5010, PlaceCategory.SHOPPING, null);
        return CourseDetailResponse.from(course, course.getDays());
    }
}
