package com.dotlo.line4thon_18team.itinerary.validation;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.PlaceInfo;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.model.TripContext;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelLookup;
import com.dotlo.line4thon_18team.itinerary.validation.result.DayTimeline;
import com.dotlo.line4thon_18team.itinerary.validation.result.IssueCode;
import com.dotlo.line4thon_18team.itinerary.validation.result.IssueKind;
import com.dotlo.line4thon_18team.itinerary.validation.result.LegSource;
import com.dotlo.line4thon_18team.itinerary.validation.result.ScheduledVisit;
import com.dotlo.line4thon_18team.itinerary.validation.result.Severity;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationIssue;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.validation.result.WindowAdjustment;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakePlaceInfoProvider;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakeTravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures;
import com.dotlo.line4thon_18team.preference.domain.DayStartPreference;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.preference.domain.TravelPace;
import com.dotlo.line4thon_18team.preference.domain.WalkingPreference;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.CAFE_CLOSED_MONDAY;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY1;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY2;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY3;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DAY4;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DOTONBORI;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.HOTEL_NAMBA;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.KIX;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.KUROMON;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.NIGHT_BAR;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.OSAKA_CASTLE;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.PARK_800M;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.RESTAURANT_NO_LOCATION;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.SHRINE_NO_HOURS;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.UMEDA_SKY;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.ZONE;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이동·영업시간 검증 (GEN-CHECK-001).
 * 대부분 모든 구간 이동시간을 15분으로 고정해 시각을 손으로 따라갈 수 있게 했다.
 * 기본 규칙: 하루 09:30 시작, 21:30까지 숙소 복귀, 방문 사이 여유 10분, 관광 90분·식당 60분·카페 45분 체류.
 */
class ItineraryValidatorTest {

    private static final PreferenceProfile NORMAL = PreferenceProfile.defaults();

    private final FakeTravelTimeProvider travel = FakeTravelTimeProvider.fixedMinutes(15);

    @Test
    void 테스트_날짜는_월요일부터다() {
        assertThat(DAY1.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
    }

    @Test
    void 이동_체류_여유시간으로_시각을_계산한다() {
        ValidationResult result = validate(travel, day(DAY1,
                visit("d1", DOTONBORI), visit("k1", KUROMON), visit("c1", OSAKA_CASTLE)));

        DayTimeline timeline = result.days().get(0);
        assertThat(timeline.window().start()).isEqualTo(DAY1.atTime(9, 30));
        assertVisit(timeline.visits().get(0), "09:45", "09:45", "11:15");
        assertVisit(timeline.visits().get(1), "11:40", "11:40", "12:40");
        assertVisit(timeline.visits().get(2), "13:05", "13:05", "14:35");
        assertThat(timeline.returnLeg().minutes()).isEqualTo(15);
        assertThat(timeline.endsAt()).isEqualTo(DAY1.atTime(14, 50));

        assertThat(timeline.visits()).extracting(v -> v.travel().source()).containsOnly(LegSource.ROUTES_API);
        assertThat(timeline.visits()).extracting(v -> v.travel().mode())
                .containsExactly(TravelMode.WALK, TravelMode.WALK, TravelMode.TRANSIT);
        assertThat(result.issues()).isEmpty();
        assertThat(result.summary().valid()).isTrue();
        assertThat(result.summary().placesNeedingCheck()).isZero();
    }

    @Test
    void 일정_강도에_따라_체류와_여유시간이_달라진다() {
        PreferenceProfile relaxed = new PreferenceProfile(TravelPace.RELAXED, WalkingPreference.NORMAL,
                DayStartPreference.NORMAL, List.of(), List.of(), List.of());

        ValidationResult result = newValidator(travel).validate(
                ItineraryDraft.of(day(DAY1, visit("d1", DOTONBORI), visit("k1", KUROMON))),
                trip(), relaxed, ValidationOrigin.AI_GENERATED);

        List<ScheduledVisit> visits = result.days().get(0).visits();
        assertThat(visits.get(0).stayMinutes()).isEqualTo(110);   // 90 × 1.2 = 108 → 110
        assertVisit(visits.get(0), "09:45", "09:45", "11:35");
        assertVisit(visits.get(1), "12:10", "12:10", "13:20");      // 여유 20분, 60 × 1.2 = 72 → 70
        assertThat(ValidationPolicy.defaults().stayMinutes(DOTONBORI.category(), TravelPace.PACKED)).isEqualTo(75);
    }

    @Test
    void 문_열기_전에_도착하면_기다렸다가_시작한다() {
        ValidationResult result = validate(travel, day(DAY2, visit("cafe", CAFE_CLOSED_MONDAY)));

        ScheduledVisit cafe = result.days().get(0).visits().get(0);
        assertVisit(cafe, "09:45", "11:00", "11:45");
        assertThat(cafe.waitMinutes()).isEqualTo(75);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void 문_열_때까지_너무_오래_기다리면_충돌이다() {
        ValidationResult result = validate(travel, day(DAY2, visit("bar", NIGHT_BAR)));

        ValidationIssue issue = single(result, IssueCode.LONG_WAIT);
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.kind()).isEqualTo(IssueKind.SCHEDULE_CONFLICT);
        assertThat(issue.params()).containsEntry("arrival", "09:45").containsEntry("start", "18:00")
                .containsEntry("waitMinutes", 495);
        assertThat(result.issues()).hasSize(1);
        assertThat(result.summary().valid()).isFalse();
        assertThat(result.summary().placesNeedingCheck()).isEqualTo(1);
    }

    @Test
    void 영업시간_밖이면_AI_일정은_오류_직접_편집은_경고다() {
        DayDraft day = day(DAY2, visit("d1", DOTONBORI).withStayMinutes(400), visit("c1", OSAKA_CASTLE));

        ValidationResult ai = validate(travel, day);
        ValidationResult edited = newValidator(travel).validate(ItineraryDraft.of(day), trip(), NORMAL,
                ValidationOrigin.USER_EDITED);

        ValidationIssue issue = single(ai, IssueCode.OUTSIDE_OPENING_HOURS);
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.itemId()).isEqualTo("c1");
        assertThat(issue.params()).containsEntry("arrival", "16:50").containsEntry("stayMinutes", 90)
                .containsEntry("openingHours", "09:00-17:00");
        assertThat(ai.summary().valid()).isFalse();

        assertThat(single(edited, IssueCode.OUTSIDE_OPENING_HOURS).severity()).isEqualTo(Severity.WARNING);
        assertThat(edited.summary().valid()).isTrue();
        assertThat(edited.summary().warningCount()).isEqualTo(1);
        assertVisit(edited.days().get(0).visits().get(1), "16:50", "16:50", "18:20");
    }

    @Test
    void 휴무일에_넣으면_충돌이다() {
        ValidationResult result = validate(travel, day(DAY1, visit("cafe", CAFE_CLOSED_MONDAY)));

        assertThat(single(result, IssueCode.CLOSED_ON_DAY).params()).containsEntry("dayOfWeek", "MONDAY");
        assertThat(result.issues()).hasSize(1);
    }

    @Test
    void 예약_시각에_맞춰_시작하고_늦으면_알려준다() {
        ValidationResult result = validate(travel,
                day(DAY2, visit("k1", KUROMON).withFixedStart(LocalTime.of(12, 0))),
                day(DAY3, visit("d3", DOTONBORI).withStayMinutes(180), visit("k3", KUROMON).withFixedStart(LocalTime.of(12, 0))));

        ScheduledVisit onTime = result.days().get(0).visits().get(0);
        assertVisit(onTime, "09:45", "12:00", "13:00");
        assertThat(onTime.waitMinutes()).isEqualTo(135);

        ValidationIssue late = single(result, IssueCode.FIXED_TIME_MISSED);
        assertThat(late.date()).isEqualTo(DAY3);
        assertThat(late.itemId()).isEqualTo("k3");
        assertThat(late.params()).containsEntry("fixedStart", "12:00").containsEntry("arrival", "13:10")
                .containsEntry("lateMinutes", 70);
        assertThat(result.issues()).hasSize(1);
    }

    @Test
    void 숙소_복귀가_하루_끝을_넘기면_충돌이다() {
        ValidationResult result = validate(travel,
                day(DAY2, visit("d1", DOTONBORI).withStayMinutes(600), visit("u1", UMEDA_SKY)));

        ValidationIssue overflow = single(result, IssueCode.DAY_OVERFLOW);
        assertThat(overflow.itemId()).isNull();
        assertThat(overflow.params()).containsEntry("endsAt", "21:55").containsEntry("dayEnd", "21:30")
                .containsEntry("overflowMinutes", 25).containsEntry("availableMinutes", 720);
        assertThat(result.summary().valid()).isFalse();
        assertThat(result.summary().placesNeedingCheck()).isZero();
    }

    @Test
    void 경로가_없으면_직선거리로_계속_계산하고_알려준다() {
        FakeTravelTimeProvider noRoute = FakeTravelTimeProvider.byDistance()
                .override(HOTEL_NAMBA, OSAKA_CASTLE.location(), TravelLookup.noRoute());
        int expected = new TravelEstimator(ValidationPolicy.defaults()).estimateMinutes(
                TravelEstimator.straightLineMeters(HOTEL_NAMBA, OSAKA_CASTLE.location()), TravelMode.TRANSIT);

        ValidationResult result = validate(noRoute, day(DAY2, visit("c1", OSAKA_CASTLE)));

        ScheduledVisit castle = result.days().get(0).visits().get(0);
        assertThat(castle.travel().source()).isEqualTo(LegSource.ROUTE_NOT_FOUND);
        assertThat(castle.travel().minutes()).isEqualTo(expected);
        assertThat(castle.travel().isEstimated()).isTrue();
        ValidationIssue issue = single(result, IssueCode.ROUTE_NOT_FOUND);
        assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        assertThat(issue.params()).containsEntry("mode", "TRANSIT").containsEntry("estimatedMinutes", expected);
        assertThat(result.summary().valid()).isTrue();
    }

    @Test
    void 경로_API_오류면_추정으로_표시한다() {
        FakeTravelTimeProvider error = FakeTravelTimeProvider.fixedMinutes(15)
                .override(HOTEL_NAMBA, KUROMON.location(), TravelLookup.error("quota exceeded"));

        ValidationResult result = validate(error, day(DAY2, visit("k1", KUROMON)));

        ScheduledVisit kuromon = result.days().get(0).visits().get(0);
        assertThat(kuromon.travel().source()).isEqualTo(LegSource.ESTIMATED);
        assertThat(kuromon.travel().mode()).isEqualTo(TravelMode.WALK);
        assertThat(single(result, IssueCode.TRAVEL_ESTIMATED).kind()).isEqualTo(IssueKind.INFO_MISSING);
    }

    @Test
    void 이동시간_함수가_예외를_던져도_검증은_끝까지_한다() {
        FakeTravelTimeProvider broken = FakeTravelTimeProvider.fixedMinutes(15)
                .failingWith(new IllegalStateException("Routes API down"));

        ValidationResult result = validate(broken, day(DAY2, visit("d1", DOTONBORI), visit("k1", KUROMON)));

        assertThat(result.issuesOf(IssueCode.TRAVEL_ESTIMATED)).hasSize(2);
        assertThat(result.days().get(0).returnLeg().source()).isEqualTo(LegSource.ESTIMATED);
        assertThat(result.days().get(0).visits()).hasSize(2);
    }

    @Test
    void 이동시간_함수가_고른_수단을_따른다() {
        // 800m라 도보로 묻지만 이동시간 함수(BE2)가 대중교통으로 답한 경우
        FakeTravelTimeProvider provider = FakeTravelTimeProvider.fixedMinutes(15)
                .override(HOTEL_NAMBA, PARK_800M.location(), TravelLookup.ok(12, 1040, TravelMode.TRANSIT));

        ValidationResult result = validate(provider, day(DAY2, visit("p1", PARK_800M)));

        assertThat(provider.queries().get(0).mode()).isEqualTo(TravelMode.WALK);
        ScheduledVisit park = result.days().get(0).visits().get(0);
        assertThat(park.travel().mode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(park.travel().minutes()).isEqualTo(12);
        assertThat(park.travel().source()).isEqualTo(LegSource.ROUTES_API);
        assertThat(result.issuesOf(IssueCode.TRAVEL_ESTIMATED)).isEmpty();
    }

    @Test
    void 이동시간_함수가_추정치를_주면_그_값을_쓰고_추정으로_표시한다() {
        FakeTravelTimeProvider provider = FakeTravelTimeProvider.fixedMinutes(15)
                .override(HOTEL_NAMBA, OSAKA_CASTLE.location(), TravelLookup.estimated(32, 4200, TravelMode.TRANSIT));

        ValidationResult result = validate(provider, day(DAY2, visit("c1", OSAKA_CASTLE)));

        ScheduledVisit castle = result.days().get(0).visits().get(0);
        assertThat(castle.travel().minutes()).isEqualTo(32);
        assertThat(castle.travel().source()).isEqualTo(LegSource.ESTIMATED);
        assertThat(castle.travel().isEstimated()).isTrue();
        ValidationIssue issue = single(result, IssueCode.TRAVEL_ESTIMATED);
        assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        assertThat(issue.params()).containsEntry("mode", "TRANSIT").containsEntry("estimatedMinutes", 32);
    }

    @Test
    void 정보가_없는_장소는_미확인으로_표시하고_계속_계산한다() {
        ValidationResult result = validate(travel, day(DAY2,
                visit("s1", SHRINE_NO_HOURS), VisitDraft.of("x1", "p-unknown"), visit("r1", RESTAURANT_NO_LOCATION)));

        assertThat(result.issues()).extracting(ValidationIssue::code).containsExactly(
                IssueCode.HOURS_UNKNOWN, IssueCode.PLACE_INFO_MISSING, IssueCode.PLACE_LOCATION_MISSING);
        assertThat(result.issues()).extracting(ValidationIssue::severity).containsOnly(Severity.WARNING);
        assertThat(result.summary().placesNeedingCheck()).isEqualTo(3);
        assertThat(result.summary().valid()).isTrue();

        List<ScheduledVisit> visits = result.days().get(0).visits();
        assertVisit(visits.get(0), "09:45", "09:45", "11:15");
        assertVisit(visits.get(1), "11:55", "11:55", "12:55");   // 좌표 없음 → 이동 30분(기본값)
        assertThat(visits.get(1).travel().source()).isEqualTo(LegSource.ESTIMATED);
        assertVisit(visits.get(2), "13:35", "13:35", "14:35");
        assertThat(result.days().get(0).endsAt()).isEqualTo(DAY2.atTime(15, 5));
    }

    @Test
    void 숙소에서_먼_첫_장소는_안내만_한다() {
        FakeTravelTimeProvider far = FakeTravelTimeProvider.fixedMinutes(15)
                .override(HOTEL_NAMBA, UMEDA_SKY.location(), TravelLookup.ok(55, 4800));

        ValidationResult result = validate(far, day(DAY2, visit("u1", UMEDA_SKY)));

        ValidationIssue issue = single(result, IssueCode.FAR_FROM_HOTEL);
        assertThat(issue.kind()).isEqualTo(IssueKind.ADVISORY);
        assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        assertThat(issue.params()).containsEntry("minutes", 55);
        assertThat(result.summary().placesNeedingCheck()).isZero();
        assertThat(result.summary().valid()).isTrue();
    }

    @Test
    void 거리와_걷기_취향으로_도보와_대중교통을_고른다() {
        FakeTravelTimeProvider normalWalker = FakeTravelTimeProvider.byDistance();
        FakeTravelTimeProvider lessWalker = FakeTravelTimeProvider.byDistance();
        PreferenceProfile walkLess = new PreferenceProfile(TravelPace.NORMAL, WalkingPreference.LESS,
                DayStartPreference.NORMAL, List.of(), List.of(), List.of());
        ItineraryDraft draft = ItineraryDraft.of(day(DAY2, visit("p1", PARK_800M)));

        newValidator(normalWalker).validate(draft, trip(), NORMAL, ValidationOrigin.AI_GENERATED);
        newValidator(lessWalker).validate(draft, trip(), walkLess, ValidationOrigin.AI_GENERATED);

        assertThat(normalWalker.queries().get(0).mode()).isEqualTo(TravelMode.WALK);
        assertThat(normalWalker.queries().get(0).departAt()).isEqualTo(DAY2.atTime(9, 30).atZone(ZONE));
        assertThat(lessWalker.queries().get(0).mode()).isEqualTo(TravelMode.TRANSIT);
    }

    @Test
    void 같은_자리의_장소_사이는_이동_조회를_생략한다() {
        PlaceInfo sameSpot = new PlaceInfo("p-dotonbori-2", "도톤보리 옆 가게", DOTONBORI.location(),
                DOTONBORI.category(), DOTONBORI.openingHours());
        FakePlaceInfoProvider places = OsakaFixtures.places().add(sameSpot);
        ItineraryValidator validator = new ItineraryValidator(places, travel, ValidationPolicy.defaults());

        ValidationResult result = validator.validate(
                ItineraryDraft.of(day(DAY2, visit("d1", DOTONBORI), visit("d2", sameSpot))),
                trip(), NORMAL, ValidationOrigin.AI_GENERATED);

        ScheduledVisit second = result.days().get(0).visits().get(1);
        assertThat(second.travel().source()).isEqualTo(LegSource.SAME_SPOT);
        assertThat(second.travel().minutes()).isZero();
        assertThat(travel.queries()).hasSize(2);   // 숙소→도톤보리, 옆 가게→숙소
        assertThat(travel.queries()).noneMatch(q -> q.from().equals(q.to()));
    }

    @Test
    void 첫날_도착_시각이_반영된다() {
        TripContext arriving = TripContext.withoutStays(ZONE, DAY1, DAY4, HOTEL_NAMBA, KIX, DAY1.atTime(13, 0), null);

        ValidationResult result = newValidator(travel).validate(
                ItineraryDraft.of(day(DAY1, visit("d1", DOTONBORI))), arriving, NORMAL, ValidationOrigin.AI_GENERATED);

        DayTimeline first = result.days().get(0);
        // 13:00 + 입국 60 + 공항→숙소 15 + 짐 정리 30 = 14:45 출발
        assertThat(first.window().start()).isEqualTo(DAY1.atTime(14, 45));
        assertThat(first.window().adjustments()).contains(WindowAdjustment.SHORTENED_BY_ARRIVAL);
        assertVisit(first.visits().get(0), "15:00", "15:00", "16:30");
    }

    @Test
    void 방문이_없는_날은_빈_시간표이고_날짜순으로_돌려준다() {
        ValidationResult result = validate(travel, day(DAY3), day(DAY2, visit("d1", DOTONBORI)));

        assertThat(result.days()).extracting(DayTimeline::date).containsExactly(DAY2, DAY3);
        DayTimeline empty = result.days().get(1);
        assertThat(empty.visits()).isEmpty();
        assertThat(empty.returnLeg()).isNull();
        assertThat(empty.endsAt()).isEqualTo(empty.window().start());
    }

    @Test
    void 입력_오류는_예외로_알린다() {
        assertThatThrownBy(() -> validate(travel, day(DAY4.plusDays(1), visit("d1", DOTONBORI))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("여행 기간 밖");
        assertThatThrownBy(() -> validate(travel, day(DAY1, visit("d1", DOTONBORI)), day(DAY2, visit("d1", KUROMON))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("itemId가 중복");
        assertThatThrownBy(() -> validate(travel, day(DAY1, visit("a", DOTONBORI)), day(DAY1, visit("b", KUROMON))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("같은 날짜");
    }

    // ---- helpers ----

    private static ItineraryValidator newValidator(FakeTravelTimeProvider provider) {
        return new ItineraryValidator(OsakaFixtures.places(), provider, ValidationPolicy.defaults());
    }

    private static ValidationResult validate(FakeTravelTimeProvider provider, DayDraft... days) {
        return newValidator(provider).validate(ItineraryDraft.of(days), trip(), NORMAL, ValidationOrigin.AI_GENERATED);
    }

    private static DayDraft day(LocalDate date, VisitDraft... visits) {
        return DayDraft.of(date, visits);
    }

    private static VisitDraft visit(String itemId, PlaceInfo place) {
        return VisitDraft.of(itemId, place.placeId());
    }

    private static ValidationIssue single(ValidationResult result, IssueCode code) {
        List<ValidationIssue> issues = result.issuesOf(code);
        assertThat(issues).as("%s 문제 개수", code).hasSize(1);
        return issues.get(0);
    }

    private static void assertVisit(ScheduledVisit visit, String arrival, String start, String end) {
        assertThat(hhmm(visit.arrival())).as("%s 도착", visit.itemId()).isEqualTo(arrival);
        assertThat(hhmm(visit.start())).as("%s 시작", visit.itemId()).isEqualTo(start);
        assertThat(hhmm(visit.end())).as("%s 종료", visit.itemId()).isEqualTo(end);
    }

    private static String hhmm(LocalDateTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
