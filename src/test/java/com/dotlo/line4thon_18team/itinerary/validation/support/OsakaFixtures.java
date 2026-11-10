package com.dotlo.line4thon_18team.itinerary.validation.support;

import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.OpeningHours;
import com.dotlo.line4thon_18team.itinerary.validation.model.PlaceInfo;
import com.dotlo.line4thon_18team.itinerary.validation.model.TripContext;
import com.dotlo.line4thon_18team.place.PlaceCategory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 오사카 3박 4일 테스트 데이터.
 * 좌표는 대략값이고, 영업시간은 테스트를 위해 정한 가정값이다 (실제 영업시간 아님).
 */
public final class OsakaFixtures {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    /** 2026-12-14 월요일 */
    public static final LocalDate DAY1 = LocalDate.of(2026, 12, 14);
    public static final LocalDate DAY2 = DAY1.plusDays(1);
    public static final LocalDate DAY3 = DAY1.plusDays(2);
    public static final LocalDate DAY4 = DAY1.plusDays(3);

    public static final GeoPoint HOTEL_NAMBA = new GeoPoint(34.6656, 135.5013);
    public static final GeoPoint KIX = new GeoPoint(34.4347, 135.2440);
    /** 난바에서 약 4.5km 북쪽 */
    public static final GeoPoint HOTEL_UMEDA = new GeoPoint(34.7055, 135.4983);

    /** 숙소에서 약 340m, 24시간 */
    public static final PlaceInfo DOTONBORI = new PlaceInfo("p-dotonbori", "도톤보리",
            new GeoPoint(34.6687, 135.5013), PlaceCategory.SIGHTSEEING, OpeningHours.alwaysOpen());
    /** 숙소에서 약 3.3km, 09:00~17:00 */
    public static final PlaceInfo OSAKA_CASTLE = new PlaceInfo("p-castle", "오사카성",
            new GeoPoint(34.6873, 135.5262), PlaceCategory.CULTURE,
            OpeningHours.daily(LocalTime.of(9, 0), LocalTime.of(17, 0)));
    /** 숙소에서 약 480m, 09:00~18:00 */
    public static final PlaceInfo KUROMON = new PlaceInfo("p-kuromon", "구로몬 시장",
            new GeoPoint(34.6654, 135.5065), PlaceCategory.FOOD,
            OpeningHours.daily(LocalTime.of(9, 0), LocalTime.of(18, 0)));
    /** 숙소에서 약 4.5km, 09:30~22:30 */
    public static final PlaceInfo UMEDA_SKY = new PlaceInfo("p-umeda-sky", "우메다 스카이 빌딩",
            new GeoPoint(34.7053, 135.4896), PlaceCategory.SIGHTSEEING,
            OpeningHours.daily(LocalTime.of(9, 30), LocalTime.of(22, 30)));
    /** 11:00~19:00, 월요일 휴무 */
    public static final PlaceInfo CAFE_CLOSED_MONDAY = new PlaceInfo("p-cafe", "카페 (월 휴무)",
            new GeoPoint(34.6700, 135.5000), PlaceCategory.CAFE,
            OpeningHours.dailyExcept(LocalTime.of(11, 0), LocalTime.of(19, 0), Set.of(DayOfWeek.MONDAY)));
    /** 18:00~다음 날 02:00 */
    public static final PlaceInfo NIGHT_BAR = new PlaceInfo("p-bar", "신세카이 바",
            new GeoPoint(34.6525, 135.5063), PlaceCategory.NIGHTLIFE,
            OpeningHours.daily(LocalTime.of(18, 0), LocalTime.of(2, 0)));
    /** 영업시간 정보 없음 */
    public static final PlaceInfo SHRINE_NO_HOURS = new PlaceInfo("p-shrine", "영업시간 없는 신사",
            new GeoPoint(34.6600, 135.5100), PlaceCategory.CULTURE, null);
    /** 좌표 없음 */
    public static final PlaceInfo RESTAURANT_NO_LOCATION = new PlaceInfo("p-no-location", "좌표 없는 식당",
            null, PlaceCategory.FOOD, OpeningHours.alwaysOpen());
    /** 숙소 바로 북쪽 약 800m (도보 기준 테스트용) */
    public static final PlaceInfo PARK_800M = new PlaceInfo("p-park", "숙소 북쪽 공원",
            new GeoPoint(34.6656 + 0.0072, 135.5013), PlaceCategory.NATURE, OpeningHours.alwaysOpen());

    private OsakaFixtures() {
    }

    /** 항공 정보 없고 숙소도 입력하지 않은 3박 4일. 난바를 기준점으로 쓴다 */
    public static TripContext trip() {
        return TripContext.withoutStays(ZONE, DAY1, DAY4, HOTEL_NAMBA, KIX, null, null);
    }

    /** 3박 모두 난바에서 묵는 3박 4일 */
    public static TripContext tripStayingAtNamba() {
        return new TripContext(ZONE, DAY1, DAY4,
                Map.of(DAY1, HOTEL_NAMBA, DAY2, HOTEL_NAMBA, DAY3, HOTEL_NAMBA),
                HOTEL_NAMBA, KIX, null, null);
    }

    /**
     * 앞 이틀은 난바, 셋째 날 밤은 우메다. 3일차가 난바에서 출발해 우메다에서 끝나는 날이다.
     * 도시를 옮기는 상황을 작게 줄인 것이다 (좌표는 같은 오사카 안이지만 4.5km 떨어져 있다).
     */
    public static TripContext tripMovingToUmeda() {
        return new TripContext(ZONE, DAY1, DAY4,
                Map.of(DAY1, HOTEL_NAMBA, DAY2, HOTEL_NAMBA, DAY3, HOTEL_UMEDA),
                HOTEL_NAMBA, KIX, null, null);
    }

    public static FakePlaceInfoProvider places() {
        return new FakePlaceInfoProvider(List.of(DOTONBORI, OSAKA_CASTLE, KUROMON, UMEDA_SKY, CAFE_CLOSED_MONDAY,
                NIGHT_BAR, SHRINE_NO_HOURS, RESTAURANT_NO_LOCATION, PARK_800M));
    }
}
