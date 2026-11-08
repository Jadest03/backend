package com.dotlo.line4thon_18team.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.place.PlaceMatcher.Candidate;
import com.dotlo.line4thon_18team.place.PlaceMatcher.MatchResult;
import com.dotlo.line4thon_18team.place.PlaceMatcher.MatchStatus;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 정보 API (숙소, 렌터카). MySQL이 필요하다 (docker compose up -d). 구글 장소 정보는 가짜로 돌려준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class ReservationControllerTest {

    private static final String APA = "ChIJapa";
    private static final String RYOKAN = "ChIJryokan";
    private static final String KIX = "ChIJkix";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @MockitoBean PlaceMatcher placeMatcher;

    private Long ownerId;
    private Long otherId;
    private Long tripId;   // 5/18 ~ 5/21 (3박)

    @BeforeEach
    void setUp() {
        User owner = users.save(User.builder().googleSub("sub-owner-rsv").email("owner@test.com").name("주인").build());
        User other = users.save(User.builder().googleSub("sub-other-rsv").email("other@test.com").name("남").build());
        tripId = trips.save(Trip.create(owner, "오사카", LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 21), Companion.ALONE, null)).getId();
        ownerId = owner.getId();
        otherId = other.getId();
        googleReturns(APA, "APA 호텔 난바", 34.66, 135.50);
        googleReturns(RYOKAN, "교토 료칸", 35.00, 135.76);
    }

    private void googleReturns(String placeId, String name, double lat, double lng) {
        when(placeMatcher.details(null, placeId)).thenReturn(new MatchResult(null, MatchStatus.MATCHED,
                new Candidate(placeId, name, "주소", lat, lng), List.of(), null, false));
    }

    private ResultActions putStays(Long userId, String body) throws Exception {
        return mvc.perform(put("/api/trips/{tripId}/reservations/stays", tripId).header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String stays(String... stays) {
        return "{\"stays\": [" + String.join(",", stays) + "]}";
    }

    private static String stay(String placeId, String checkIn, String checkOut) {
        return "{\"placeId\":\"" + placeId + "\",\"checkIn\":\"" + checkIn + "\",\"checkOut\":\"" + checkOut + "\"}";
    }

    @Test
    void 숙소를_밤별로_나눠_저장하고_조회한다() throws Exception {
        // Figma 예시: APA 호텔 2박(18·19일 밤) + 교토 료칸 1박(20일 밤)
        putStays(ownerId, stays(stay(RYOKAN, "2026-05-20", "2026-05-21"), stay(APA, "2026-05-18", "2026-05-20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays.length()").value(2))
                .andExpect(jsonPath("$.stays[0].name").value("APA 호텔 난바"))   // checkIn 순서
                .andExpect(jsonPath("$.stays[0].checkOut").value("2026-05-20"))
                .andExpect(jsonPath("$.stays[1].placeId").value(RYOKAN))
                .andExpect(jsonPath("$.stays[1].lat").value(35.00));

        mvc.perform(get("/api/trips/{tripId}/reservations", tripId).header("X-User-Id", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.stays.length()").value(2));
    }

    @Test
    void 다시_저장하면_보낸_목록으로_바뀌고_이미_있던_숙소는_구글을_다시_부르지_않는다() throws Exception {
        putStays(ownerId, stays(stay(APA, "2026-05-18", "2026-05-21"))).andExpect(status().isOk());

        putStays(ownerId, stays(stay(APA, "2026-05-18", "2026-05-20"), stay(RYOKAN, "2026-05-20", "2026-05-21")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays.length()").value(2))
                .andExpect(jsonPath("$.stays[0].checkOut").value("2026-05-20"));

        verify(placeMatcher, times(1)).details(null, APA);
        verify(placeMatcher, times(1)).details(null, RYOKAN);
    }

    @Test
    void 빈_목록을_보내면_숙소를_모두_지운다() throws Exception {
        putStays(ownerId, stays(stay(APA, "2026-05-18", "2026-05-21"))).andExpect(status().isOk());

        putStays(ownerId, stays())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays.length()").value(0));
    }

    @Test
    void 비는_밤이_있어도_저장한다() throws Exception {
        putStays(ownerId, stays(stay(APA, "2026-05-19", "2026-05-20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays.length()").value(1));
    }

    @Test
    void 날짜가_잘못되면_400이고_구글을_부르지_않는다() throws Exception {
        String[] wrong = {
                stays(stay(APA, "2026-05-17", "2026-05-19")),                                        // 여행 시작 전
                stays(stay(APA, "2026-05-20", "2026-05-22")),                                        // 여행 끝난 뒤
                stays(stay(APA, "2026-05-19", "2026-05-19")),                                        // 0박
                stays(stay(APA, "2026-05-18", "2026-05-20"), stay(RYOKAN, "2026-05-19", "2026-05-21")) // 19일 밤이 겹침
        };
        for (String body : wrong) {
            putStays(ownerId, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_STAY_DATES"));
        }
        verify(placeMatcher, never()).details(any(), any());
    }

    @Test
    void 값이_빠지면_400이다() throws Exception {
        putStays(ownerId, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        putStays(ownerId, stays("{\"placeId\":\"\",\"checkIn\":\"2026-05-18\",\"checkOut\":\"2026-05-19\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        putStays(ownerId, stays("{\"placeId\":\"" + APA + "\",\"checkIn\":\"2026-05-18\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_구글_장소면_400이고_기존_숙소는_그대로다() throws Exception {
        putStays(ownerId, stays(stay(APA, "2026-05-18", "2026-05-21"))).andExpect(status().isOk());
        when(placeMatcher.details(null, "ChIJnone")).thenThrow(new BusinessException(ErrorCode.INVALID_PLACE_ID));

        putStays(ownerId, stays(stay("ChIJnone", "2026-05-18", "2026-05-21")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_ID"));

        mvc.perform(get("/api/trips/{tripId}/reservations", tripId).header("X-User-Id", ownerId))
                .andExpect(jsonPath("$.stays[0].placeId").value(APA));
    }

    private ResultActions putRental(Long userId, String body) throws Exception {
        return mvc.perform(put("/api/trips/{tripId}/reservations/rental", tripId).header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 렌터카를_저장하고_조회한다() throws Exception {
        googleReturns(KIX, "간사이 국제공항", 34.43, 135.24);
        putStays(ownerId, stays(stay(APA, "2026-05-18", "2026-05-21"))).andExpect(status().isOk());

        // 쓰는 날은 이어지지 않아도 되고 날짜순으로 돌려준다
        putRental(ownerId, "{\"dates\": [\"2026-05-21\", \"2026-05-19\"], \"placeId\": \"" + KIX + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rental.dates[0]").value("2026-05-19"))
                .andExpect(jsonPath("$.rental.dates[1]").value("2026-05-21"))
                .andExpect(jsonPath("$.rental.name").value("간사이 국제공항"))
                .andExpect(jsonPath("$.stays.length()").value(1));   // 숙소는 그대로

        mvc.perform(get("/api/trips/{tripId}/reservations", tripId).header("X-User-Id", ownerId))
                .andExpect(jsonPath("$.rental.placeId").value(KIX));
    }

    @Test
    void 렌터카를_다시_저장하면_바뀌고_같은_장소면_구글을_다시_부르지_않는다() throws Exception {
        googleReturns(KIX, "간사이 국제공항", 34.43, 135.24);
        putRental(ownerId, "{\"dates\": [\"2026-05-19\"], \"placeId\": \"" + KIX + "\"}").andExpect(status().isOk());

        putRental(ownerId, "{\"dates\": [\"2026-05-20\", \"2026-05-21\"], \"placeId\": \"" + KIX + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rental.dates.length()").value(2))
                .andExpect(jsonPath("$.rental.dates[0]").value("2026-05-20"));

        verify(placeMatcher, times(1)).details(null, KIX);
    }

    @Test
    void 쓰는_날을_비워_보내면_렌터카를_지운다() throws Exception {
        googleReturns(KIX, "간사이 국제공항", 34.43, 135.24);
        putRental(ownerId, "{\"dates\": [\"2026-05-19\"], \"placeId\": \"" + KIX + "\"}").andExpect(status().isOk());

        putRental(ownerId, "{\"dates\": []}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rental").doesNotExist());
    }

    @Test
    void 렌터카_날짜가_기간_밖이거나_장소가_없으면_400이고_구글을_부르지_않는다() throws Exception {
        String[] wrong = {
                "{\"dates\": [\"2026-05-17\"], \"placeId\": \"" + KIX + "\"}",   // 여행 시작 전
                "{\"dates\": [\"2026-05-22\"], \"placeId\": \"" + KIX + "\"}",   // 여행 끝난 뒤
                "{\"dates\": [\"2026-05-19\"]}",                                  // 픽업 장소 없음
                "{\"dates\": [\"2026-05-19\"], \"placeId\": \" \"}"
        };
        for (String body : wrong) {
            putRental(ownerId, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RENTAL"));
        }
        putRental(ownerId, "{\"placeId\": \"" + KIX + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verify(placeMatcher, never()).details(any(), any());
    }

    @Test
    void 남의_여행이면_404다() throws Exception {
        putStays(otherId, stays(stay(APA, "2026-05-18", "2026-05-21")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        putRental(otherId, "{\"dates\": []}")
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/{tripId}/reservations", tripId).header("X-User-Id", otherId))
                .andExpect(status().isNotFound());
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mvc.perform(get("/api/trips/{tripId}/reservations", tripId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }
}
