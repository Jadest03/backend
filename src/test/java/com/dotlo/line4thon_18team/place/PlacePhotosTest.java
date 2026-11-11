package com.dotlo.line4thon_18team.place;

import static org.junit.jupiter.api.Assertions.*;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlacePhotos.Photo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class PlacePhotosTest {

    // 실제 API 호출. 키가 환경변수에 있을 때만 (CI에서는 건너뜀). 사진 1장 요금이 나감
    @Test
    @EnabledIfEnvironmentVariable(named = "GOOGLE_MAPS_API_KEY", matches = ".+")
    void 실제_API() {
        PlacePhotos service = new PlacePhotos(System.getenv("GOOGLE_MAPS_API_KEY"));

        // 이치란 우메다시바타점: 사진 10장 (2026-11-11 확인)
        Photo p = service.get("ChIJt9Y375HmAGARejKVNQwlkbI", 0, 400);
        assertTrue(p.photoUrl().startsWith("https://lh3.googleusercontent.com/"), p.photoUrl());
        assertEquals(10, p.totalCount());
        assertFalse(p.attributions().isEmpty());

        BusinessException over = assertThrows(BusinessException.class,
                () -> service.get("ChIJt9Y375HmAGARejKVNQwlkbI", 10, 400));
        assertEquals(ErrorCode.PHOTO_NOT_FOUND, over.getErrorCode());

        BusinessException bad = assertThrows(BusinessException.class, () -> service.get("abc", 0, 400));
        assertEquals(ErrorCode.INVALID_PLACE_ID, bad.getErrorCode());

        System.out.println("사진: " + p);
    }
}
