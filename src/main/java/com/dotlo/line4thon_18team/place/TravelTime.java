package com.dotlo.line4thon_18team.place;

import com.dotlo.line4thon_18team.place.PlaceMatcher.LatLng;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** BE2 이동시간. BE3(일정 검증)는 이 서비스를 주입받아 직접 호출합니다. */
@Service
public class TravelTime {

    private static final Logger log = LoggerFactory.getLogger(TravelTime.class);

    public enum Mode { WALK, TRANSIT, DRIVE }

    /*
    - estimated: true면 실제 경로가 아닌 추정치입니다. 화면에 '추정'(예상 시간)으로 표시합니다.
      일본처럼 API에 대중교통 경로가 없는 곳, API 오류일 때 true.
    - distanceMeters: 경로 거리. 대중교통 추정(자동차 시간 + 10분)은 자동차 길 거리, 직선거리 추정은 직선거리 × 1.3.
    */
    public record Leg(int minutes, Mode mode, boolean estimated, int distanceMeters) {}

    // Routes API 응답 중 필요한 필드만
    record RoutesResponse(List<Route> routes) {}
    record Route(String duration, Integer distanceMeters, List<RouteLeg> legs) {}  // duration: "3061s". 거리 0이면 빠짐
    record RouteLeg(List<Step> steps) {}
    record Step(String travelMode) {}

    private static final String ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    // 요금은 요청 기능(교통정보 반영 등)으로 정해지고 받는 필드와는 상관없음 (2026-11-16 SKU 문서 확인)
    private static final String FIELD_MASK = "routes.duration,routes.distanceMeters,routes.legs.steps.travelMode";
    private static final int WALK_MAX_MIN = 20;
    private static final int TRANSIT_EXTRA_MIN = 10;  // 자동차 시간 → 대중교통 추정: 역까지 걷기, 기다리기
    private static final int TRANSIT_SCHEDULE_DAYS = 90;  // 이보다 먼 출발은 가까운 날짜로 바꿔 조회
    private static final int DRIVE_M_PER_MIN = 500;       // 직선거리 추정용 자동차 속도 (시속 30km)

    private final String apiKey;
    private final RestClient rest = GoogleClient.create();

    public TravelTime(@Value("${google.maps.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /*
    1. 대중교통 경로가 있으면 → 대중교통 시간
    2. 없으면(일본 등) 걸어서 20분 이내 → 도보 시간
    3. 더 멀면 → 자동차 시간 + 10분 (대중교통 추정)
    4. API 오류, 경로 없음 → 직선거리로 추정
    departure: 출발 시각(대중교통 시간표용). null이면 지금.
    */
    public Leg get(LatLng from, LatLng to, Instant departure) {
        try {
            Route transit = route(from, to, "TRANSIT", transitDeparture(departure, Instant.now()));
            if (transit != null && hasTransitStep(transit)) return leg(transit, Mode.TRANSIT);

            // 대중교통 경로가 걷기뿐이면 그 시간이 도보 시간과 같음 (2026-11-01 오사카에서 확인). 경로가 없으면 도보로 다시 조회.
            Route walk = transit != null ? transit : route(from, to, "WALK", null);
            if (walk != null && minutes(walk) <= WALK_MAX_MIN) return leg(walk, Mode.WALK);

            Route drive = route(from, to, "DRIVE", null);
            if (drive != null) return transitFromDrive(leg(drive, Mode.DRIVE), walk == null ? null : leg(walk, Mode.WALK), from, to);
        } catch (RestClientException e) {
            log.warn("Routes API 오류, 직선거리로 추정: {}", e.getMessage());
        }
        return straightLine(from, to);
    }

    /*
    사용자가 도보를 고른 구간: 멀어도 도보 시간.
    경로 없음·API 오류 → 직선거리로 추정
    */
    public Leg walk(LatLng from, LatLng to) {
        try {
            Route walk = route(from, to, "WALK", null);
            if (walk != null) return leg(walk, Mode.WALK);
        } catch (RestClientException e) {
            log.warn("Routes API 오류, 직선거리로 추정: {}", e.getMessage());
        }
        return straightLineWalk(from, to);
    }

    /*
    사용자가 대중교통을 고른 구간: 가까워도 대중교통.
    대중교통 경로가 없거나(일본 등) 대중교통 조회가 실패하면 이동 수단 선택 화면(options)과 같은 규칙으로 추정
    */
    public Leg transit(LatLng from, LatLng to, Instant departure) {
        Leg transit = transitRoute(from, to, departure);
        return transit != null ? transit : transitFromDrive(drive(from, to), walk(from, to), from, to);
    }

    /*
    구간 이동 수단 선택 화면: 도보·대중교통·자동차 순서로 3개.
    자동차 경로를 한 번만 불러서 대중교통 추정에도 쓴다 (구글 호출 최대 3번)
    */
    public List<Leg> options(LatLng from, LatLng to, Instant departure) {
        Leg walk = walk(from, to);
        Leg drive = drive(from, to);
        Leg transit = transitRoute(from, to, departure);
        if (transit == null) transit = transitFromDrive(drive, walk, from, to);
        return List.of(walk, transit, drive);
    }

    // 대중교통 경로. 경로가 없거나(일본 등) API 오류면 null
    private Leg transitRoute(LatLng from, LatLng to, Instant departure) {
        try {
            Route route = route(from, to, "TRANSIT", transitDeparture(departure, Instant.now()));
            if (route != null && hasTransitStep(route)) return leg(route, Mode.TRANSIT);
        } catch (RestClientException e) {
            log.warn("Routes API 오류(대중교통), 자동차 경로로 추정: {}", e.getMessage());
        }
        return null;
    }

    /*
    렌터카 쓰는 날: 자동차 시간 그대로 (get()의 대중교통 추정과 달리 +10분 없음).
    경로 없음·API 오류 → 직선거리로 추정
    */
    public Leg drive(LatLng from, LatLng to) {
        try {
            Route drive = route(from, to, "DRIVE", null);
            if (drive != null) return leg(drive, Mode.DRIVE);
        } catch (RestClientException e) {
            log.warn("Routes API 오류, 직선거리로 추정: {}", e.getMessage());
        }
        return straightLineDrive(from, to);
    }

    // 구글은 약 100일 뒤까지만 대중교통 경로를 줌 (2026-11-06 방콕: 101일 뒤 있음, 105일 뒤 없음 → 추정치로 떨어짐).
    // 90일보다 먼 출발은 같은 요일·같은 시각인 7~13일 뒤 날짜로 바꿔 조회. ponytail: 공휴일·시간표 개편은 무시
    static Instant transitDeparture(Instant departure, Instant now) {
        if (departure == null || departure.isBefore(now.plus(TRANSIT_SCHEDULE_DAYS, ChronoUnit.DAYS))) return departure;
        long weeks = ChronoUnit.DAYS.between(now, departure) / 7 - 1;
        return departure.minus(weeks * 7, ChronoUnit.DAYS);
    }

    // 실제 경로. 거리는 구글 값
    private static Leg leg(Route r, Mode mode) {
        return new Leg(minutes(r), mode, false, r.distanceMeters() == null ? 0 : r.distanceMeters());
    }

    // 대중교통 경로가 없을 때: 자동차 시간 + 10분, 거리는 자동차 길. 자동차도 추정치(경로 없음·오류)면 직선거리로 추정
    // 이 추정이 도보보다 길면 도보 시간·거리 (예: 108m인데 일방통행으로 자동차 길 1.8km → 22분). walk: 모르면 null
    // 자동 선택(get), 고른 대중교통(transit), 선택 화면(options)이 모두 이 규칙을 씀
    static Leg transitFromDrive(Leg drive, Leg walk, LatLng from, LatLng to) {
        Leg transit = drive.estimated() ? straightLineTransit(from, to)
                : new Leg(drive.minutes() + TRANSIT_EXTRA_MIN, Mode.TRANSIT, true, drive.distanceMeters());
        return walk != null && walk.minutes() < transit.minutes()
                ? new Leg(walk.minutes(), Mode.TRANSIT, true, walk.distanceMeters()) : transit;
    }

    // ponytail: 굽은 길 1.3배, 도보 분당 80m, 대중교통 분당 300m + 10분. 실제 값과 비교해서 조정.
    public static Leg straightLine(LatLng from, LatLng to) {
        Leg walk = straightLineWalk(from, to);
        return walk.minutes() <= WALK_MAX_MIN ? walk : straightLineTransit(from, to);
    }

    static Leg straightLineWalk(LatLng from, LatLng to) {
        double m = roadMeters(from, to);
        return new Leg((int) Math.ceil(m / 80), Mode.WALK, true, (int) Math.round(m));
    }

    static Leg straightLineTransit(LatLng from, LatLng to) {
        double m = roadMeters(from, to);
        return new Leg((int) Math.ceil(m / 300) + TRANSIT_EXTRA_MIN, Mode.TRANSIT, true, (int) Math.round(m));
    }

    // ponytail: 굽은 길 1.3배, 자동차 분당 500m(시내 기준). 실제 값과 비교해서 조정.
    static Leg straightLineDrive(LatLng from, LatLng to) {
        double m = roadMeters(from, to);
        return new Leg((int) Math.ceil(m / DRIVE_M_PER_MIN), Mode.DRIVE, true, (int) Math.round(m));
    }

    private static double roadMeters(LatLng from, LatLng to) {
        return PlaceMatcher.meters(from.lat(), from.lng(), to.lat(), to.lng()) * 1.3;
    }

    static int minutes(Route r) {
        long sec = Long.parseLong(r.duration().replace("s", ""));
        return (int) Math.ceil(sec / 60.0);
    }

    private static boolean hasTransitStep(Route r) {
        if (r.legs() == null) return false;
        return r.legs().stream()
                .flatMap(l -> l.steps() == null ? java.util.stream.Stream.<Step>empty() : l.steps().stream())
                .anyMatch(s -> "TRANSIT".equals(s.travelMode()));
    }

    // 경로가 없으면 null
    private Route route(LatLng from, LatLng to, String mode, Instant departure) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", waypoint(from));
        body.put("destination", waypoint(to));
        body.put("travelMode", mode);
        if (departure != null) body.put("departureTime", departure.toString());
        RoutesResponse res = rest.post().uri(ROUTES_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", FIELD_MASK)
                .body(body)
                .retrieve()
                .body(RoutesResponse.class);
        if (res == null || res.routes() == null || res.routes().isEmpty()) return null;  // 경로 없으면 {} 가 옴
        return res.routes().get(0);
    }

    private static Map<String, Object> waypoint(LatLng p) {
        return Map.of("location", Map.of("latLng", Map.of("latitude", p.lat(), "longitude", p.lng())));
    }
}
