package com.dotlo.line4thon_18team.place;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** BE2 장소 사진. 구글 정책상 사진 이름은 저장할 수 없어서("You cannot cache a photo name") 볼 때마다 placeId로 다시 받습니다. */
@Service
public class PlacePhotos {

    /*
    - photoUrl: 구글 이미지 주소. 키 없이 열려서 FE가 <img src>에 그대로 넣음.
    - totalCount: 이 장소의 사진 수 (최대 10). 상세 갤러리 "1/10" 표시용.
    - attributions: 사진 올린 사람. 사진 근처에 표시해야 함 (구글 정책).
    */
    public record Photo(String photoUrl, int totalCount, List<Attribution> attributions) {}
    public record Attribution(String name, String uri) {}

    // Places API 응답 중 필요한 필드만
    record Details(List<GooglePhoto> photos) {}
    record GooglePhoto(String name, List<Author> authorAttributions) {}
    record Author(String displayName, String uri) {}
    record Media(String photoUri) {}

    private static final String DETAILS_URL = "https://places.googleapis.com/v1/places/{placeId}";
    private static final String API_BASE = "https://places.googleapis.com/v1/";

    private final String apiKey;
    private final RestClient rest = GoogleClient.create();

    public PlacePhotos(@Value("${google.maps.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    // index번째 사진 (0부터, 구글이 최대 10장만 줌). 사진이 없거나 index가 넘치면 PHOTO_NOT_FOUND
    public Photo get(String placeId, int index, int maxWidth) {
        // 사진 목록(photos)만 받으면 Place Details Essentials IDs Only 요금 = 무료 (2026-11-11 요금표 확인)
        Details d;
        try {
            d = rest.get().uri(DETAILS_URL, placeId)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "photos")
                    .retrieve()
                    .body(Details.class);
        } catch (HttpClientErrorException.BadRequest e) {
            // 키가 틀려도 400이라 메시지로 구분: "The provided Place ID: ... is not valid." (2026-11-11 확인)
            if (e.getResponseBodyAsString().contains("Place ID")) throw new BusinessException(ErrorCode.INVALID_PLACE_ID);
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_ID);
        }
        List<GooglePhoto> photos = d == null || d.photos() == null ? List.of() : d.photos();
        if (index < 0 || index >= photos.size()) throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
        GooglePhoto p = photos.get(index);

        // 여기서 사진 1장 요금 (Place Details Photos). skipHttpRedirect=true면 이미지 대신 주소를 JSON으로 줌
        // name이 "places/{id}/photos/{ref}"라 {변수}로 넣으면 /가 인코딩돼서 그대로 붙임
        Media m = rest.get().uri(API_BASE + p.name() + "/media?maxWidthPx={w}&skipHttpRedirect=true", maxWidth)
                .header("X-Goog-Api-Key", apiKey)
                .retrieve()
                .body(Media.class);
        if (m == null || m.photoUri() == null) throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);

        List<Attribution> by = p.authorAttributions() == null ? List.of()
                : p.authorAttributions().stream().map(a -> new Attribution(a.displayName(), a.uri())).toList();
        return new Photo(m.photoUri(), photos.size(), by);
    }
}
