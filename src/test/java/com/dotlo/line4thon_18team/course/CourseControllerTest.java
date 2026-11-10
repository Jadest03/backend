package com.dotlo.line4thon_18team.course;

import com.dotlo.line4thon_18team.course.entity.Course;
import com.dotlo.line4thon_18team.course.repository.CourseRepository;
import com.dotlo.line4thon_18team.course.service.CourseService;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 MySQL 조회부터 JSON 응답까지 확인한다. 외부 API 호출은 필요하지 않다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseControllerTest {

    @Autowired MockMvc mvc;
    @Autowired CourseRepository courses;
    @Autowired CourseService service;
    @Autowired EntityManager em;
    @Autowired ObjectMapper mapper;

    private Long kyotoId;
    private Long newerKyotoId;
    private Long osakaId;

    @BeforeEach
    void setUp() {
        Course kyoto = Course.create("교토 골목 산책", "일본", "교토", 3);
        kyoto.describe("아침부터 천천히 걷는 코스", "https://example.test/kyoto.jpg", "도보와 버스");
        kyoto.credit("등록자", "https://example.test/author.jpg", "직접 다녀온 여행", YearMonth.of(2026, 3));
        kyoto.changeTags(List.of("산책", "전통"));
        // 저장 순서와 일차 순서가 다르며, 2일차 행은 없다.
        kyoto.addDay(3).addStop("test-first", "첫 장소 재방문", 34.99, 135.78,
                PlaceCategory.CULTURE, LocalTime.of(18, 0));
        var day1 = kyoto.addDay(1);
        day1.addStop("test-first", "첫 장소", 34.99, 135.78, PlaceCategory.CULTURE,
                LocalTime.of(9, 0)).changeImage("https://example.test/place.jpg");
        day1.addStop("test-second", "두 번째 장소", 34.98, 135.77, PlaceCategory.CAFE, null);
        kyotoId = courses.saveAndFlush(kyoto).getId();
        newerKyotoId = courses.saveAndFlush(Course.create("새 교토 코스", "일본", "교토", 1)).getId();
        Course osaka = Course.create("오사카 코스", "일본", "오사카", 1);
        osaka.addDay(1).addStop("test-osaka", "오사카 장소", 34.66, 135.50, PlaceCategory.FOOD, null);
        osakaId = courses.saveAndFlush(osaka).getId();
        em.clear();
    }

    @Test
    void 로그인_없이_전체_목록을_최신순으로_조회한다() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(osakaId))
                .andExpect(jsonPath("$[1].id").value(newerKyotoId))
                .andExpect(jsonPath("$[2].id").value(kyotoId))
                .andExpect(jsonPath("$[2].stopCount").value(3))
                .andExpect(jsonPath("$[2].tags.length()").value(2))
                .andExpect(jsonPath("$[2].author.travelMonth").value("2026-03"))
                .andExpect(jsonPath("$[2].days").doesNotExist());
    }

    @Test
    void 도시_앞뒤_공백을_제거하고_해당_도시만_조회한다() throws Exception {
        mvc.perform(get("/api/courses").param("city", " 교토 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newerKyotoId))
                .andExpect(jsonPath("$[0].stopCount").value(0))
                .andExpect(jsonPath("$[1].city").value("교토"))
                .andExpect(jsonPath("$[1].stopCount").value(3));
    }

    @Test
    void 등록된_코스가_없는_도시는_빈_배열이다() throws Exception {
        mvc.perform(get("/api/courses").param("city", "없는 도시"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void 빈_도시_이름은_400이다(String city) throws Exception {
        mvc.perform(get("/api/courses").param("city", city))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 도시_이름이_100자를_넘으면_400이다() throws Exception {
        mvc.perform(get("/api/courses").param("city", "도".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 로그인_없이_상세와_일차별_방문_순서를_조회한다() throws Exception {
        mvc.perform(get("/api/courses/{courseId}", kyotoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(kyotoId))
                .andExpect(jsonPath("$.title").value("교토 골목 산책"))
                .andExpect(jsonPath("$.description").value("아침부터 천천히 걷는 코스"))
                .andExpect(jsonPath("$.dayCount").value(3))
                .andExpect(jsonPath("$.nightCount").value(2))
                .andExpect(jsonPath("$.stopCount").value(3))
                .andExpect(jsonPath("$.transportSummary").value("도보와 버스"))
                .andExpect(jsonPath("$.author.name").value("등록자"))
                .andExpect(jsonPath("$.author.travelMonth").value("2026-03"))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].dayNumber").value(1))
                .andExpect(jsonPath("$.days[0].stopCount").value(2))
                .andExpect(jsonPath("$.days[0].stops[0].position").value(1))
                .andExpect(jsonPath("$.days[0].stops[0].placeId").value("test-first"))
                .andExpect(jsonPath("$.days[0].stops[0].category").value("CULTURE"))
                .andExpect(jsonPath("$.days[0].stops[0].visitTime").value("09:00:00"))
                .andExpect(jsonPath("$.days[0].stops[0].latitude").value(34.99))
                .andExpect(jsonPath("$.days[0].stops[1].position").value(2))
                .andExpect(jsonPath("$.days[0].stops[1].visitTime").value(nullValue()))
                .andExpect(jsonPath("$.days[1].dayNumber").value(2))
                .andExpect(jsonPath("$.days[1].stops").isEmpty())
                .andExpect(jsonPath("$.days[2].dayNumber").value(3));
    }

    @Test
    void 장소와_작성자_정보가_없는_코스도_빈_일차와_null로_돌려준다() throws Exception {
        mvc.perform(get("/api/courses/{courseId}", newerKyotoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopCount").value(0))
                .andExpect(jsonPath("$.nightCount").value(0))
                .andExpect(jsonPath("$.author.name").value(nullValue()))
                .andExpect(jsonPath("$.author.travelMonth").value(nullValue()))
                .andExpect(jsonPath("$.tags").isEmpty())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].stops").isEmpty());
    }

    @Test
    void 없는_코스는_404이다() throws Exception {
        mvc.perform(get("/api/courses/{courseId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void 잘못된_코스_id는_400이다(String id) throws Exception {
        mvc.perform(get("/api/courses/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 목록과_상세는_각각_조회_두_번으로_완성하고_엔티티를_노출하지_않는다() throws Exception {
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            var list = service.list(null);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            assertThat(list).hasSize(3);
            em.clear();
            mapper.writeValueAsString(list);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

            statistics.clear();
            var detail = service.get(kyotoId);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            assertThat(detail.days()).hasSize(3);
            em.clear();
            mapper.writeValueAsString(detail);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
