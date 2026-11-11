package com.dotlo.line4thon_18team.place.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.place.PlacePhotos;
import com.dotlo.line4thon_18team.place.PlacePhotos.Photo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlacePhotoController {

    private final PlacePhotos placePhotos;

    // 장소 사진 1장. 목록 썸네일은 index 0, 상세 갤러리는 넘길 때마다 다음 index
    // 사진마다 구글 요금이 나가서 로그인 필요 (로그인 없이 열면 누구나 우리 요금을 씀)
    @GetMapping("/api/places/{placeId}/photos/{index}")
    public Photo photo(@LoginUser Long userId, @PathVariable String placeId, @PathVariable int index,
                       @RequestParam(defaultValue = "400") int maxWidth) {
        return placePhotos.get(placeId, index, Math.max(100, Math.min(1600, maxWidth)));
    }
}
