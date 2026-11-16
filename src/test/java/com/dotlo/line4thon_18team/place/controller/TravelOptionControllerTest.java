package com.dotlo.line4thon_18team.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dotlo.line4thon_18team.auth.jwt.JwtProvider;
import com.dotlo.line4thon_18team.global.config.SecurityConfig;
import com.dotlo.line4thon_18team.place.PlaceMatcher.LatLng;
import com.dotlo.line4thon_18team.place.TravelTime;
import com.dotlo.line4thon_18team.place.TravelTime.Leg;
import com.dotlo.line4thon_18team.place.TravelTime.Mode;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(TravelOptionController.class)
@Import({SecurityConfig.class, JwtProvider.class})
@TestPropertySource(properties = "auth.dev-header-enabled=true")
class TravelOptionControllerTest {

    private static final LatLng NAMBA = new LatLng(34.6687, 135.5013);
    private static final LatLng CASTLE = new LatLng(34.6873, 135.5262);

    @Autowired MockMvc mvc;
    @MockitoBean TripService tripService;
    @MockitoBean TravelTime travelTime;

    @BeforeEach
    void osakaTrip() {
        Trip trip = mock(Trip.class);
        when(trip.getCity()).thenReturn("오사카");
        when(tripService.findMemberTrip(1L, 7L)).thenReturn(trip);
    }

    @Test
    void 도보_대중교통_자동차를_출발_현지_시각으로_조회한다() throws Exception {
        // 오사카 12/10 11:30 = UTC 02:30
        when(travelTime.options(NAMBA, CASTLE, Instant.parse("2026-12-10T02:30:00Z"))).thenReturn(List.of(
                new Leg(40, Mode.WALK, false, 3100),
                new Leg(25, Mode.TRANSIT, true, 3800),
                new Leg(15, Mode.DRIVE, false, 3800)));

        mvc.perform(request(NAMBA, CASTLE).param("departAt", "2026-12-10T11:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].mode").value("WALK"))
                .andExpect(jsonPath("$[0].minutes").value(40))
                .andExpect(jsonPath("$[0].distanceMeters").value(3100))
                .andExpect(jsonPath("$[1].mode").value("TRANSIT"))
                .andExpect(jsonPath("$[1].estimated").value(true));
    }

    @Test
    void 출발_시각이_없으면_지금_기준() throws Exception {
        when(travelTime.options(NAMBA, CASTLE, null)).thenReturn(List.of());

        mvc.perform(request(NAMBA, CASTLE)).andExpect(status().isOk());

        verify(travelTime).options(NAMBA, CASTLE, null);
    }

    @Test
    void 여행_도시_밖이나_잘못된_좌표는_400이고_구글을_부르지_않는다() throws Exception {
        mvc.perform(request(NAMBA, new LatLng(35.6764, 139.6500)))   // 도쿄
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(get("/api/trips/7/travel-options").header("X-User-Id", "1")
                        .param("fromLat", "NaN").param("fromLng", "135.5013")
                        .param("toLat", "34.6873").param("toLng", "135.5262"))
                .andExpect(status().isBadRequest());
        mvc.perform(request(NAMBA, CASTLE).param("departAt", "내일"))
                .andExpect(status().isBadRequest());

        verify(travelTime, never()).options(any(), any(), any());
    }

    @Test
    void 로그인_없음_401() throws Exception {
        mvc.perform(get("/api/trips/7/travel-options")
                        .param("fromLat", "34.6687").param("fromLng", "135.5013")
                        .param("toLat", "34.6873").param("toLng", "135.5262"))
                .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder request(LatLng from, LatLng to) {
        return get("/api/trips/7/travel-options").header("X-User-Id", "1")
                .param("fromLat", String.valueOf(from.lat())).param("fromLng", String.valueOf(from.lng()))
                .param("toLat", String.valueOf(to.lat())).param("toLng", String.valueOf(to.lng()));
    }
}
