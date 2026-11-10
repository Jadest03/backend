package com.dotlo.line4thon_18team.itinerary.validation;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.OpeningHours;
import com.dotlo.line4thon_18team.itinerary.validation.model.PlaceInfo;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.model.TripContext;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.port.PlaceInfoProvider;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelLookup;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelQuery;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.result.DayTimeline;
import com.dotlo.line4thon_18team.itinerary.validation.result.DayWindow;
import com.dotlo.line4thon_18team.itinerary.validation.result.IssueCode;
import com.dotlo.line4thon_18team.itinerary.validation.result.LegSource;
import com.dotlo.line4thon_18team.itinerary.validation.result.ScheduledVisit;
import com.dotlo.line4thon_18team.itinerary.validation.result.TravelLeg;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationIssue;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * GEN-CHECK-001 이동·영업시간 검증.
 *
 * <p>일정의 방문 순서만 받아서 실제 시각을 직접 계산한다 (AI가 이동시간·영업시간을 지어내지 않도록).
 * <pre>
 *   숙소 출발 → [이동시간] → 도착 → (영업 시작·예약 시각까지 대기) → 체류 → 여유시간 → 다음 장소 ...
 *   → 마지막 장소에서 숙소 복귀
 * </pre>
 * 계산한 시각을 영업시간·휴무·예약 시각·하루 끝 시각과 대조해 문제를 모은다.
 * 이동시간은 BE2 함수에서 받고, 실패하면 직선거리 추정치로 계속 계산한다.
 *
 * <p>호출하는 곳: AI 일정 생성·텍스트 수정(내부), 직접 편집 저장 후, 인기 코스 담기 후, 확정 시 경고 개수.
 */
public final class ItineraryValidator {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final PlaceInfoProvider places;
    private final TravelTimeProvider travel;
    private final ValidationPolicy policy;
    private final TravelEstimator estimator;
    private final DayWindowCalculator windowCalculator;

    public ItineraryValidator(PlaceInfoProvider places, TravelTimeProvider travel, ValidationPolicy policy) {
        this.places = Objects.requireNonNull(places, "places");
        this.travel = Objects.requireNonNull(travel, "travel");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.estimator = new TravelEstimator(policy);
        this.windowCalculator = new DayWindowCalculator(travel, policy);
    }

    /**
     * @throws IllegalArgumentException 여행 기간 밖 날짜, 같은 날짜 중복, itemId 중복
     */
    public ValidationResult validate(ItineraryDraft draft, TripContext trip, PreferenceProfile preferences,
                                     ValidationOrigin origin) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(trip, "trip");
        Objects.requireNonNull(preferences, "preferences");
        Objects.requireNonNull(origin, "origin");
        checkInput(draft, trip);

        Map<LocalDate, DayWindow> windows = new LinkedHashMap<>();
        for (DayWindow window : windowCalculator.calculate(trip, preferences)) {
            windows.put(window.date(), window);
        }
        Map<String, PlaceInfo> placeInfos = findPlaces(draft);

        Issues issues = new Issues(origin);
        List<DayTimeline> timelines = new ArrayList<>();
        draft.days().stream()
                .sorted(Comparator.comparing(DayDraft::date))
                .forEach(day -> timelines.add(
                        buildDay(day, windows.get(day.date()), trip, preferences, placeInfos, issues)));
        return ValidationResult.of(timelines, issues.list);
    }

    private DayTimeline buildDay(DayDraft day, DayWindow window, TripContext trip, PreferenceProfile preferences,
                                 Map<String, PlaceInfo> placeInfos, Issues issues) {
        LocalDate date = day.date();
        LocalDateTime cursor = window.start();
        GeoPoint previousLocation = trip.startOf(date);
        List<ScheduledVisit> visits = new ArrayList<>();

        for (int i = 0; i < day.visits().size(); i++) {
            VisitDraft visit = day.visits().get(i);
            PlaceInfo info = placeInfos.get(visit.placeId());
            if (info == null) {
                issues.add(IssueCode.PLACE_INFO_MISSING, date, visit, Map.of());
            } else if (info.location() == null) {
                issues.add(IssueCode.PLACE_LOCATION_MISSING, date, visit, Map.of());
            }
            GeoPoint location = info == null ? null : info.location();

            TravelLeg leg = leg(previousLocation, location, cursor, trip, preferences, issues, date, visit);
            if (i == 0 && leg.minutes() > policy.farFromHotelMinutes()) {
                issues.add(IssueCode.FAR_FROM_HOTEL, date, visit, Map.of("minutes", leg.minutes()));
            }
            LocalDateTime arrival = cursor.plusMinutes(leg.minutes());
            int stay = visit.stayMinutes() != null
                    ? visit.stayMinutes()
                    : policy.stayMinutes(info == null ? PlaceCategory.OTHER : info.category(), preferences.pace());
            LocalDateTime start = decideStart(visit, info, arrival, stay, date, issues);
            LocalDateTime end = start.plusMinutes(stay);

            visits.add(new ScheduledVisit(visit.itemId(), visit.placeId(), leg, arrival, start, end,
                    minutesBetween(arrival, start), stay));
            cursor = end.plusMinutes(policy.bufferMinutes(preferences.pace()));
            previousLocation = location;
        }

        if (visits.isEmpty()) {
            return new DayTimeline(date, window, visits, null, window.start());
        }
        ScheduledVisit last = visits.get(visits.size() - 1);
        TravelLeg returnLeg = leg(previousLocation, trip.endOf(date), last.end(), trip, preferences, null, date, null);
        LocalDateTime endsAt = last.end().plusMinutes(returnLeg.minutes());
        if (endsAt.isAfter(window.end())) {
            issues.add(IssueCode.DAY_OVERFLOW, date, null, params(
                    "endsAt", hhmm(endsAt),
                    "dayEnd", hhmm(window.end()),
                    "overflowMinutes", minutesBetween(window.end(), endsAt),
                    "availableMinutes", window.availableMinutes()));
        }
        return new DayTimeline(date, window, visits, returnLeg, endsAt);
    }

    /** 도착 시각에서 실제 방문 시작 시각을 정하고, 영업시간·예약 시각 문제를 기록한다. */
    private LocalDateTime decideStart(VisitDraft visit, PlaceInfo info, LocalDateTime arrival, int stay,
                                      LocalDate date, Issues issues) {
        LocalDateTime start = arrival;
        boolean fixed = visit.fixedStart() != null;
        if (fixed) {
            LocalDateTime fixedAt = date.atTime(visit.fixedStart());
            if (arrival.isAfter(fixedAt)) {
                issues.add(IssueCode.FIXED_TIME_MISSED, date, visit, params(
                        "fixedStart", hhmm(fixedAt),
                        "arrival", hhmm(arrival),
                        "lateMinutes", minutesBetween(fixedAt, arrival)));
            } else {
                start = fixedAt;
            }
        }
        if (info == null) {
            return start;
        }
        OpeningHours hours = info.openingHours();
        if (hours == null) {
            issues.add(IssueCode.HOURS_UNKNOWN, date, visit, Map.of());
            return start;
        }
        if (hours.isClosedOn(date)) {
            issues.add(IssueCode.CLOSED_ON_DAY, date, visit, Map.of("dayOfWeek", date.getDayOfWeek().name()));
            return start;
        }
        if (fixed) {
            if (!hours.covers(start, start.plusMinutes(stay))) {
                issues.add(IssueCode.OUTSIDE_OPENING_HOURS, date, visit, params(
                        "arrival", hhmm(start),
                        "stayMinutes", stay,
                        "openingHours", hours.describe(date)));
            }
            return start;
        }
        Optional<LocalDateTime> opened = hours.earliestStart(arrival, stay);
        if (opened.isEmpty()) {
            issues.add(IssueCode.OUTSIDE_OPENING_HOURS, date, visit, params(
                    "arrival", hhmm(arrival),
                    "stayMinutes", stay,
                    "openingHours", hours.describe(date)));
            return arrival;
        }
        int wait = minutesBetween(arrival, opened.get());
        if (wait > policy.longWaitMinutes()) {
            issues.add(IssueCode.LONG_WAIT, date, visit, params(
                    "arrival", hhmm(arrival),
                    "start", hhmm(opened.get()),
                    "waitMinutes", wait));
        }
        return opened.get();
    }

    /**
     * 이동 구간 계산. 직선거리가 도보 기준 이하면 도보, 넘으면 대중교통으로 조회한다.
     * 이동시간 함수가 수단을 직접 고르면(BE2) 그 수단을 따르고, 추정치를 주면 '추정'으로 표시한다.
     * issues가 null이면(숙소 복귀 구간) 문제를 기록하지 않는다.
     */
    private TravelLeg leg(GeoPoint from, GeoPoint to, LocalDateTime departAt, TripContext trip,
                          PreferenceProfile preferences, Issues issues, LocalDate date, VisitDraft visit) {
        if (from == null || to == null) {
            return new TravelLeg(TravelMode.TRANSIT, policy.unknownLegMinutes(), 0, LegSource.ESTIMATED);
        }
        double straight = TravelEstimator.straightLineMeters(from, to);
        if (straight <= policy.sameSpotMeters()) {
            return new TravelLeg(TravelMode.WALK, 0, (int) Math.round(straight), LegSource.SAME_SPOT);
        }
        TravelMode mode = straight <= policy.walkThresholdMeters(preferences.walking())
                ? TravelMode.WALK : TravelMode.TRANSIT;
        TravelLookup lookup = TravelLookups.safe(travel,
                new TravelQuery(from, to, mode, departAt.atZone(trip.zone())));
        if (lookup.status() == TravelLookup.Status.OK) {
            TravelMode actual = lookup.mode() != null ? lookup.mode() : mode;
            if (!lookup.estimated()) {
                return new TravelLeg(actual, lookup.durationMinutes(), lookup.distanceMeters(), LegSource.ROUTES_API);
            }
            if (issues != null) {
                issues.add(IssueCode.TRAVEL_ESTIMATED, date, visit,
                        params("mode", actual.name(), "estimatedMinutes", lookup.durationMinutes()));
            }
            return new TravelLeg(actual, lookup.durationMinutes(), lookup.distanceMeters(), LegSource.ESTIMATED);
        }

        int estimated = estimator.estimateMinutes(straight, mode);
        boolean noRoute = lookup.status() == TravelLookup.Status.NO_ROUTE;
        if (issues != null) {
            issues.add(noRoute ? IssueCode.ROUTE_NOT_FOUND : IssueCode.TRAVEL_ESTIMATED, date, visit,
                    params("mode", mode.name(), "estimatedMinutes", estimated));
        }
        return new TravelLeg(mode, estimated, estimator.estimateDistanceMeters(straight),
                noRoute ? LegSource.ROUTE_NOT_FOUND : LegSource.ESTIMATED);
    }

    private Map<String, PlaceInfo> findPlaces(ItineraryDraft draft) {
        Set<String> placeIds = new LinkedHashSet<>();
        draft.days().forEach(day -> day.visits().forEach(visit -> placeIds.add(visit.placeId())));
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, PlaceInfo> found = places.findPlaces(placeIds);
        return found == null ? Map.of() : found;
    }

    private static void checkInput(ItineraryDraft draft, TripContext trip) {
        Set<LocalDate> dates = new HashSet<>();
        Set<String> itemIds = new HashSet<>();
        for (DayDraft day : draft.days()) {
            if (!trip.covers(day.date())) {
                throw new IllegalArgumentException("여행 기간 밖의 날짜입니다: " + day.date());
            }
            if (!dates.add(day.date())) {
                throw new IllegalArgumentException("같은 날짜가 두 번 들어 있습니다: " + day.date());
            }
            for (VisitDraft visit : day.visits()) {
                if (!itemIds.add(visit.itemId())) {
                    throw new IllegalArgumentException("itemId가 중복됩니다: " + visit.itemId());
                }
            }
        }
    }

    private static int minutesBetween(LocalDateTime from, LocalDateTime to) {
        return (int) Duration.between(from, to).toMinutes();
    }

    private static String hhmm(LocalDateTime time) {
        return time.format(HH_MM);
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /** 문제 수집 + 심각도 결정. */
    private static final class Issues {
        private final ValidationOrigin origin;
        private final List<ValidationIssue> list = new ArrayList<>();

        private Issues(ValidationOrigin origin) {
            this.origin = origin;
        }

        void add(IssueCode code, LocalDate date, VisitDraft visit, Map<String, Object> params) {
            list.add(new ValidationIssue(code, code.severityFor(origin), code.kind(), date,
                    visit == null ? null : visit.itemId(),
                    visit == null ? null : visit.placeId(),
                    params));
        }
    }
}
