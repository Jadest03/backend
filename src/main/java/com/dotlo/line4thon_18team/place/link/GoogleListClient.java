package com.dotlo.line4thon_18team.place.link;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.importer.ImportedList;
import com.dotlo.line4thon_18team.place.importer.ImportedPlace;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공식 API 가 아니라 구글맵 웹이 쓰는 내부 주소(entitylist/getlist)를 읽는 방식이라, 구글이 구조를 바꾸면 깨질 수 있다.
 * 사용자가 준 URL 로 서버가 요청을 보내기 때문에, 구글 주소만 허용하고 리다이렉트도 한 번씩 확인한다 (SSRF 방지).
 */
@Component
public class GoogleListClient {

    private static final int MAX_REDIRECTS = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    // getlist 는 브라우저가 아니면 거절해서 브라우저처럼 요청한다. 단축 링크는 기본 UA 여야 302 리다이렉트를 준다
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    // google.com, google.co.kr, google.com.au, google.de ... (단축 링크가 나라별 주소로 리다이렉트된다)
    private static final Pattern GOOGLE_HOST = Pattern.compile("(www\\.)?google\\.(com|[a-z]{2}|co\\.[a-z]{2}|com\\.[a-z]{2})");
    private static final Pattern GETLIST_URL = Pattern.compile("href=\"([^\"]*entitylist/getlist[^\"]*)\"");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();
    private final JsonMapper jsonMapper = new JsonMapper();

    public ImportedList fetch(String url) {
        URI uri = toListUri(url);
        String html = followRedirects(uri);

        Matcher getlist = GETLIST_URL.matcher(html);
        if (!getlist.find()) {
            // 장소 1개 링크, 리스트가 아닌 링크, 삭제된 리스트
            throw new BusinessException(ErrorCode.LIST_NOT_ACCESSIBLE);
        }
        URI apiUri = URI.create("https://www.google.com").resolve(getlist.group(1).replace("&amp;", "&"));
        if (!isGoogleHost(apiUri.getHost())) {
            throw new BusinessException(ErrorCode.LIST_NOT_ACCESSIBLE);
        }

        HttpResponse<String> response = send(apiUri, BROWSER_USER_AGENT);
        if (response.statusCode() != 200) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
        return parse(response.body());
    }

    private URI toListUri(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_LIST_URL);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean shortLink = host.equals("maps.app.goo.gl") || (host.equals("goo.gl") && path.startsWith("/maps"));
        boolean mapsPage = isGoogleHost(host) && path.startsWith("/maps");
        if (!https || !(shortLink || mapsPage)) {
            throw new BusinessException(ErrorCode.INVALID_LIST_URL);
        }
        return uri;
    }

    private String followRedirects(URI uri) {
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpResponse<String> response = send(uri, null);
            int status = response.statusCode();
            if (status / 100 != 3) {
                if (status != 200) {
                    throw new BusinessException(ErrorCode.LIST_NOT_ACCESSIBLE);   // 404: 없는 링크
                }
                return response.body();
            }
            URI next = uri.resolve(response.headers().firstValue("location")
                    .orElseThrow(() -> new BusinessException(ErrorCode.EXTERNAL_API_ERROR)));
            String host = next.getHost() == null ? "" : next.getHost().toLowerCase();
            if (!"https".equalsIgnoreCase(next.getScheme())
                    || !(isGoogleHost(host) || host.equals("maps.app.goo.gl") || host.equals("goo.gl"))) {
                throw new BusinessException(ErrorCode.LIST_NOT_ACCESSIBLE);   // 구글 밖으로 보내는 링크
            }
            uri = next;
        }
        throw new BusinessException(ErrorCode.LIST_NOT_ACCESSIBLE);
    }

    private HttpResponse<String> send(URI uri, String userAgent) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET();
        if (userAgent != null) {
            request.header("User-Agent", userAgent);
        }
        try {
            return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private static boolean isGoogleHost(String host) {
        return host != null && GOOGLE_HOST.matcher(host.toLowerCase()).matches();
    }

    /**
     * getlist 응답 읽기. 구조 (2026-11 확인):
     *   [0]    리스트. 비공개 리스트면 null
     *   [0][4] 리스트 이름
     *   [0][8] 장소들. 장소마다 [2] 이름, [1][5] = [_, _, 위도, 경도], [1][6] = ["숫자", "숫자"] feature ID
     */
    ImportedList parse(String raw) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(raw.substring(Math.max(raw.indexOf('['), 0)));   // 앞의 )]}' 제거
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "구글 응답을 읽지 못했습니다.");
        }
        JsonNode list = root.path(0);
        if (!list.isArray()) {
            throw new BusinessException(ErrorCode.LIST_PRIVATE);
        }

        String listName = list.path(4).isString() && !list.path(4).asString().isBlank()
                ? list.path(4).asString().trim() : "공유 리스트";
        List<ImportedPlace> places = new ArrayList<>();
        int skippedRows = 0;
        for (JsonNode item : list.path(8)) {
            ImportedPlace place = toPlace(item);
            if (place == null) {
                skippedRows++;
            } else {
                places.add(place);
            }
        }
        return new ImportedList(listName, places, skippedRows);
    }

    private ImportedPlace toPlace(JsonNode item) {
        String name = item.path(2).isString() ? item.path(2).asString().trim() : "";
        JsonNode info = item.path(1);
        JsonNode coordinates = info.path(5);
        Double lat = coordinates.path(2).isNumber() ? coordinates.path(2).asDouble() : null;
        Double lng = coordinates.path(3).isNumber() ? coordinates.path(3).asDouble() : null;
        JsonNode ids = info.path(6);

        if (ids.path(0).isString() && ids.path(1).isString() && !name.isEmpty()) {
            // 부호 있는 10진수 2개 → Takeout 과 같은 "0x...:0x..." (음수는 부호 없는 16진수로)
            long first = Long.parseLong(ids.path(0).asString());
            long second = Long.parseLong(ids.path(1).asString());
            String featureId = "0x" + Long.toHexString(first) + ":0x" + Long.toHexString(second);
            String googleUrl = "https://maps.google.com/?cid=" + Long.toUnsignedString(second);
            return new ImportedPlace(name, null, googleUrl, featureId, lat, lng);
        }
        if (lat != null && lng != null) {
            String googleUrl = "https://www.google.com/maps/search/" + lat + "," + lng;
            return new ImportedPlace(name.isEmpty() ? lat + ", " + lng : name, null, googleUrl, null, lat, lng);
        }
        return null;
    }
}
