package com.dotlo.line4thon_18team.course;

import com.dotlo.line4thon_18team.course.entity.Course;
import com.dotlo.line4thon_18team.course.entity.CourseDay;
import com.dotlo.line4thon_18team.course.entity.CourseStop;
import com.dotlo.line4thon_18team.course.repository.CourseRepository;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 MySQL에서 일차·방문 순서와 자식 행의 생명주기를 확인한다. 끝나면 롤백된다. */
@SpringBootTest
@Transactional
class CourseRepositoryTest {

    @Autowired CourseRepository courses;
    @Autowired EntityManager em;

    @Test
    void 코스와_일차별_장소를_저장하고_순서대로_다시_읽는다() {
        Course course = Course.create("골목을 걷다 만나는 교토의 느린 하루", "일본", "교토", 3);
        course.describe("유명한 곳은 이른 아침에 방문하는 코스", "https://example.test/cover.jpg", "도보와 버스");
        course.credit("산책하는 유진", "https://example.test/author.jpg", "직접 다녀온 여행", YearMonth.of(2026, 3));
        course.changeTags(List.of("산책", "전통", "산책"));
        // 입력 순서가 2일차 → 1일차여도 읽을 때는 일차 순이다. 3일차는 장소 없는 날로 등록 가능하다.
        addPlace(course.addDay(2), "test-yasaka", "야사카 탑", LocalTime.of(16, 30));
        CourseDay first = course.addDay(1);
        CourseStop kiyomizu = addPlace(first, "test-kiyomizu", "기요미즈데라", LocalTime.of(9, 0));
        kiyomizu.changeImage("https://example.test/place.jpg");
        addPlace(first, "test-sannenzaka", "산넨자카 골목", LocalTime.of(10, 30));
        course.addDay(3);
        Long id = courses.saveAndFlush(course).getId();
        em.clear();

        Course loaded = courses.findById(id).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo(course.getTitle());
        assertThat(loaded.getCountry()).isEqualTo("일본");
        assertThat(loaded.getCity()).isEqualTo("교토");
        assertThat(loaded.getDescription()).isEqualTo("유명한 곳은 이른 아침에 방문하는 코스");
        assertThat(loaded.getCoverImageUrl()).isEqualTo("https://example.test/cover.jpg");
        assertThat(loaded.getAuthorName()).isEqualTo("산책하는 유진");
        assertThat(loaded.getAuthorImageUrl()).isEqualTo("https://example.test/author.jpg");
        assertThat(loaded.getSourceLabel()).isEqualTo("직접 다녀온 여행");
        assertThat(loaded.getTravelMonth()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(loaded.getTransportSummary()).isEqualTo("도보와 버스");
        assertThat(loaded.getTags()).containsExactly("산책", "전통");
        assertThat(loaded.getDayCount()).isEqualTo(3);
        assertThat(loaded.getNightCount()).isEqualTo(2);
        assertThat(loaded.getStopCount()).isEqualTo(3);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getDays()).extracting(CourseDay::getDayNumber).containsExactly(1, 2, 3);
        List<CourseStop> stops = loaded.getDays().get(0).getStops();
        assertThat(stops).extracting(CourseStop::getPosition).containsExactly(1, 2);
        assertThat(stops).extracting(CourseStop::getPlaceId).containsExactly("test-kiyomizu", "test-sannenzaka");
        assertThat(stops.get(0).getVisitTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(stops.get(0).getImageUrl()).isEqualTo("https://example.test/place.jpg");
        assertThat(stops.get(0).getCategory()).isEqualTo(PlaceCategory.CULTURE);
        assertThat(stops.get(0).getLatitude()).isEqualTo(34.99);
        assertThat(stops.get(0).getLongitude()).isEqualTo(135.78);
        assertThat(loaded.getDays().get(2).getStops()).isEmpty();
    }

    @Test
    void 도시별_코스만_최신순으로_조회한다() {
        Course older = courses.saveAndFlush(Course.create("교토 첫 코스", "일본", "교토", 1));
        Course newer = courses.saveAndFlush(Course.create("교토 다음 코스", "일본", "교토", 2));
        courses.saveAndFlush(Course.create("오사카 코스", "일본", "오사카", 1));
        em.clear();

        assertThat(courses.findAllByCityOrderByCreatedAtDescIdDesc("교토"))
                .extracting(Course::getId).containsExactly(newer.getId(), older.getId());
        assertThat(courses.findAllByCityOrderByCreatedAtDescIdDesc("등록 안 한 도시")).isEmpty();
    }

    @Test
    void 코스를_삭제하면_일차와_방문장소와_태그도_함께_지운다() {
        Course course = Course.create("삭제할 코스", "일본", "교토", 1);
        course.changeTags(List.of("산책"));
        CourseDay day = course.addDay(1);
        CourseStop stop = addPlace(day, "test-kiyomizu", "기요미즈데라", null);
        Long id = courses.saveAndFlush(course).getId();
        Long dayId = day.getId();
        Long stopId = stop.getId();
        em.clear();

        courses.deleteById(id);
        courses.flush();
        em.clear();

        assertThat(courses.findById(id)).isEmpty();
        assertThat(em.find(CourseDay.class, dayId)).isNull();
        assertThat(em.find(CourseStop.class, stopId)).isNull();
        Number remainingTags = (Number) em.createNativeQuery("select count(*) from course_tags where course_id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        assertThat(remainingTags.longValue()).isZero();
    }

    @Test
    void 같은_장소를_다른_일차에_다시_방문할_수_있다() {
        Course course = Course.create("두 번 방문", "일본", "교토", 2);
        addPlace(course.addDay(1), "test-same", "같은 장소", LocalTime.of(9, 0));
        addPlace(course.addDay(2), "test-same", "같은 장소", LocalTime.of(18, 0));
        Long id = courses.saveAndFlush(course).getId();
        em.clear();

        Course loaded = courses.findById(id).orElseThrow();
        assertThat(loaded.getStopCount()).isEqualTo(2);
        assertThat(loaded.getDays().get(0).getStops().get(0).getVisitTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(loaded.getDays().get(1).getStops().get(0).getVisitTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void 작성자와_사진과_방문시각이_없어도_원본을_저장한다() {
        Course course = Course.create("직접 등록한 당일 코스", "일본", "교토", 1);
        addPlace(course.addDay(1), "test-kiyomizu", "기요미즈데라", null);
        Long id = courses.saveAndFlush(course).getId();
        em.clear();

        Course loaded = courses.findById(id).orElseThrow();
        assertThat(loaded.getAuthorName()).isNull();
        assertThat(loaded.getTravelMonth()).isNull();
        assertThat(loaded.getCoverImageUrl()).isNull();
        assertThat(loaded.getTags()).isEmpty();
        assertThat(loaded.getNightCount()).isZero();
        assertThat(loaded.getDays().get(0).getStops().get(0).getVisitTime()).isNull();
    }

    private static CourseStop addPlace(CourseDay day, String placeId, String name, LocalTime visitTime) {
        return day.addStop(placeId, name, 34.99, 135.78, PlaceCategory.CULTURE, visitTime);
    }
}
