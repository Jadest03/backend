package com.dotlo.line4thon_18team.place;

import static org.junit.jupiter.api.Assertions.*;

import com.dotlo.line4thon_18team.place.PlaceMatcher.LatLng;
import com.dotlo.line4thon_18team.place.TravelTime.Leg;
import com.dotlo.line4thon_18team.place.TravelTime.Mode;
import com.dotlo.line4thon_18team.place.TravelTime.Route;
import java.time.Instant;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class TravelTimeTest {

    static final LatLng 미도스지점 = new LatLng(34.6692878, 135.5006524);
    static final LatLng 하루카스 = new LatLng(34.6463062, 135.5133091);
    static final LatLng 글리코사인 = new LatLng(34.6687, 135.5013);

    @Test
    void 시간_변환() {
        assertEquals(52, TravelTime.minutes(new Route("3061s", null, null)));  // 51분 1초 → 52분
        assertEquals(1, TravelTime.minutes(new Route("60s", null, null)));
    }

    @Test
    void 직선거리_추정() {
        Leg near = TravelTime.straightLine(미도스지점, 글리코사인);
        assertEquals(new Leg(2, Mode.WALK, true, road(미도스지점, 글리코사인)), near);
        Leg far = TravelTime.straightLine(미도스지점, 하루카스);  // 직선 약 2.8km
        assertEquals(new Leg(23, Mode.TRANSIT, true, road(미도스지점, 하루카스)), far);
    }

    @Test
    void 자동차_직선거리_추정() {
        // 직선 약 2.8km × 1.3 ÷ 분당 500m → 8분
        assertEquals(new Leg(8, Mode.DRIVE, true, road(미도스지점, 하루카스)), TravelTime.straightLineDrive(미도스지점, 하루카스));
    }

    @Test
    void 고른_수단의_직선거리_추정은_수단을_바꾸지_않는다() {
        // 약 3.6km 도보 → 46분이어도 도보, 200m 대중교통 → 대중교통 추정 (10분 대기 포함)
        assertEquals(Mode.WALK, TravelTime.straightLineWalk(미도스지점, 하루카스).mode());
        assertEquals(46, TravelTime.straightLineWalk(미도스지점, 하루카스).minutes());
        assertEquals(new Leg(11, Mode.TRANSIT, true, road(미도스지점, 글리코사인)), TravelTime.straightLineTransit(미도스지점, 글리코사인));
    }

    // 직선거리 × 1.3
    private static int road(LatLng from, LatLng to) {
        return (int) Math.round(PlaceMatcher.meters(from.lat(), from.lng(), to.lat(), to.lng()) * 1.3);
    }

    @Test
    void 먼_출발은_같은_요일_같은_시각의_가까운_날로_바꾼다() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        Instant far = Instant.parse("2027-03-16T03:00:00Z");   // 130일 뒤 화요일 12시(KST)
        Instant shifted = TravelTime.transitDeparture(far, now);
        long days = java.time.Duration.between(now, shifted).toDays();
        assertTrue(days >= 7 && days <= 13, "며칠 뒤: " + days);
        assertEquals(0, java.time.Duration.between(shifted, far).toDays() % 7);          // 같은 요일
        assertEquals(far.atZone(ZoneOffset.UTC).toLocalTime(), shifted.atZone(ZoneOffset.UTC).toLocalTime());  // 같은 시각

        Instant soon = Instant.parse("2026-12-10T03:00:00Z");  // 34일 뒤는 그대로
        assertEquals(soon, TravelTime.transitDeparture(soon, now));
        assertNull(TravelTime.transitDeparture(null, now));
    }

    // 실제 API 호출. 키가 환경변수에 있을 때만 (CI에서는 건너뜀)
    @Test
    @EnabledIfEnvironmentVariable(named = "GOOGLE_MAPS_API_KEY", matches = ".+")
    void 실제_API() {
        TravelTime service = new TravelTime(System.getenv("GOOGLE_MAPS_API_KEY"));
        Instant noonKst = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(3, 0).toInstant(ZoneOffset.UTC);

        // 오사카 가까운 곳 → 도보
        Leg near = service.get(미도스지점, 글리코사인, noonKst);
        assertEquals(Mode.WALK, near.mode());
        assertFalse(near.estimated());

        // 오사카 먼 곳 → 대중교통 경로 없음 → 자동차 + 10분 추정
        Leg far = service.get(미도스지점, 하루카스, noonKst);
        assertEquals(Mode.TRANSIT, far.mode());
        assertTrue(far.estimated());
        assertTrue(far.minutes() >= 15 && far.minutes() <= 40, "분: " + far.minutes());

        // 서울역 → 강남역 → 실제 대중교통
        Leg seoul = service.get(new LatLng(37.5547, 126.9706), new LatLng(37.4979, 127.0276), noonKst);
        assertEquals(Mode.TRANSIT, seoul.mode());
        assertFalse(seoul.estimated());

        // 방콕 130일 뒤 → 구글은 경로를 안 주지만 가까운 날로 바꿔서 실제 대중교통 (2026-11-06 확인)
        Instant later = noonKst.plus(130, java.time.temporal.ChronoUnit.DAYS);
        Leg bangkok = service.get(new LatLng(13.7465, 100.5348), new LatLng(13.7500, 100.4913), later);
        assertEquals(Mode.TRANSIT, bangkok.mode());
        assertFalse(bangkok.estimated());

        // 렌터카 날 → 자동차 시간 그대로. 위 오사카 추정(자동차 + 10분)에서 10분을 뺀 값과 같다
        Leg car = service.drive(미도스지점, 하루카스);
        assertEquals(Mode.DRIVE, car.mode());
        assertFalse(car.estimated());
        assertEquals(far.minutes() - 10, car.minutes());

        // 경로 거리: 도보·자동차 모두 직선거리(약 2.8km)보다 길고, 대중교통 추정은 자동차 길 거리
        assertTrue(near.distanceMeters() > 0);
        assertEquals(car.distanceMeters(), far.distanceMeters());
        assertTrue(car.distanceMeters() > 2800 && car.distanceMeters() < 8000, "자동차 m: " + car.distanceMeters());

        // 사용자가 고른 수단: 먼 곳도 도보, 가까운 곳도 대중교통(일본은 자동차 + 10분 추정)
        Leg longWalk = service.walk(미도스지점, 하루카스);
        assertEquals(Mode.WALK, longWalk.mode());
        assertFalse(longWalk.estimated());
        assertTrue(longWalk.minutes() > 20, "도보 분: " + longWalk.minutes());
        Leg shortTransit = service.transit(미도스지점, 글리코사인, noonKst);
        assertEquals(Mode.TRANSIT, shortTransit.mode());
        assertTrue(shortTransit.estimated());

        // 이동 수단 선택 시트: 도보·대중교통·자동차 순서
        List<Leg> options = service.options(미도스지점, 하루카스, noonKst);
        assertEquals(List.of(Mode.WALK, Mode.TRANSIT, Mode.DRIVE), options.stream().map(Leg::mode).toList());
        assertEquals(far, options.get(1));
        assertEquals(car, options.get(2));

        System.out.println("도보: " + near + " / 오사카 추정: " + far + " / 서울: " + seoul + " / 방콕 130일 뒤: " + bangkok
                + " / 오사카 자동차: " + car + " / 먼 도보: " + longWalk + " / 가까운 대중교통: " + shortTransit);
    }
}
