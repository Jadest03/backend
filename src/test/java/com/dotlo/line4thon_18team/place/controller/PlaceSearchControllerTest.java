package com.dotlo.line4thon_18team.place.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.dotlo.line4thon_18team.auth.jwt.JwtProvider;
import com.dotlo.line4thon_18team.global.config.SecurityConfig;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.place.PlaceMatcher.Candidate;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaceSearchController.class)
@Import({SecurityConfig.class, JwtProvider.class})
@TestPropertySource(properties = "auth.dev-header-enabled=true")
class PlaceSearchControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean TripService tripService;
    @MockitoBean PlaceMatcher placeMatcher;

    @Test
    void 여행_도시_중심_50km에서_검색() throws Exception {
        Trip trip = mock(Trip.class);
        when(trip.getCity()).thenReturn("오사카");
        when(tripService.findMemberTrip(1L, 7L)).thenReturn(trip);
        when(placeMatcher.searchNear("이치란", 34.6952474, 135.5012079, 50_000)).thenReturn(List.of(
                new Candidate("ChIJt9Y375HmAGARejKVNQwlkbI", "이치란 우메다시바타점", "오사카 기타구", 34.7, 135.5)));

        mvc.perform(get("/api/places/search").param("query", "이치란").param("tripId", "7").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placeId").value("ChIJt9Y375HmAGARejKVNQwlkbI"))
                .andExpect(jsonPath("$[0].name").value("이치란 우메다시바타점"));
    }

    @Test
    void 검색어_없음_400() throws Exception {
        mvc.perform(get("/api/places/search").param("tripId", "7").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(get("/api/places/search").param("query", " ").param("tripId", "7").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verify(placeMatcher, never()).searchNear(anyString(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void 로그인_없음_401() throws Exception {
        mvc.perform(get("/api/places/search").param("query", "이치란").param("tripId", "7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }
}
