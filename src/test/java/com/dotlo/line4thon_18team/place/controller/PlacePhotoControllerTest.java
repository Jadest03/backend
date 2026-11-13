package com.dotlo.line4thon_18team.place.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.dotlo.line4thon_18team.auth.jwt.JwtProvider;
import com.dotlo.line4thon_18team.global.config.SecurityConfig;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlacePhotos;
import com.dotlo.line4thon_18team.place.PlacePhotos.Attribution;
import com.dotlo.line4thon_18team.place.PlacePhotos.Photo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlacePhotoController.class)
@Import({SecurityConfig.class, JwtProvider.class})
@TestPropertySource(properties = "auth.dev-header-enabled=true")
class PlacePhotoControllerTest {

    static final String ICHIRAN = "ChIJt9Y375HmAGARejKVNQwlkbI";

    @Autowired MockMvc mvc;
    @MockitoBean PlacePhotos placePhotos;

    @Test
    void 사진_주소와_올린_사람() throws Exception {
        when(placePhotos.get(ICHIRAN, 0, 400)).thenReturn(new Photo("https://lh3.googleusercontent.com/a=w400", 10,
                List.of(new Attribution("一蘭 梅田芝田店", "https://maps.google.com/maps/contrib/1"))));

        mvc.perform(get("/api/places/{placeId}/photos/0", ICHIRAN).header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://lh3.googleusercontent.com/a=w400"))
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.attributions[0].name").value("一蘭 梅田芝田店"))
                .andExpect(jsonPath("$.attributions[0].uri").value("https://maps.google.com/maps/contrib/1"));
    }

    @Test
    void 가로_크기는_100에서_1600_사이로_맞춤() throws Exception {
        mvc.perform(get("/api/places/{placeId}/photos/1", ICHIRAN).param("maxWidth", "5000").header("X-User-Id", "1"));
        mvc.perform(get("/api/places/{placeId}/photos/1", ICHIRAN).param("maxWidth", "10").header("X-User-Id", "1"));
        verify(placePhotos).get(ICHIRAN, 1, 1600);
        verify(placePhotos).get(ICHIRAN, 1, 100);
    }

    @Test
    void 사진_없음_404() throws Exception {
        when(placePhotos.get(ICHIRAN, 10, 400)).thenThrow(new BusinessException(ErrorCode.PHOTO_NOT_FOUND));

        mvc.perform(get("/api/places/{placeId}/photos/10", ICHIRAN).header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
    }

    @Test
    void 로그인_없음_401() throws Exception {
        mvc.perform(get("/api/places/{placeId}/photos/0", ICHIRAN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        verify(placePhotos, never()).get(anyString(), anyInt(), anyInt());
    }
}
