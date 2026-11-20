package com.dotlo.line4thon_18team.course.service;

import com.dotlo.line4thon_18team.city.CityController;
import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse;
import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse.Day;
import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse.Stop;
import com.dotlo.line4thon_18team.place.PlaceMatcher.LatLng;
import com.dotlo.line4thon_18team.place.TravelTime;
import com.dotlo.line4thon_18team.place.TravelTime.Leg;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 공개 코스 상세의 구간 이동 (같은 날 앞 장소 → 이 장소). 구글 약관상 경로 결과는 저장할 수 없어 볼 때마다 조회한다.
 * 구간당 구글 경로 조회 최대 3번 (코스 하나 구간 3~9개). 아래 경우는 구글 대신 직선거리로 추정한다 (estimated=true).
 * <ul>
 *   <li>오늘 조회한 구간이 하루 상한을 넘김: 비회원 API가 일정 검증·AI 일정 생성과 같은 키의 하루 할당량을 다 쓰지 않게</li>
 *   <li>구간 조회가 3초 안에 안 끝남, 오류</li>
 * </ul>
 */
@Slf4j
@Component
public class CourseTravel {

    // 하루 구간 1,000개 = 경로 조회 최대 3,000번. 구글 하루 할당량(ComputeRoutes 9,000)의 1/3
    private static final int DAILY_LEGS = 1000;
    private static final Duration LEG_TIMEOUT = Duration.ofSeconds(3);
    private static final LocalTime DEFAULT_DEPART = LocalTime.of(10, 0);
    private static final ZoneId QUOTA_ZONE = ZoneId.of("America/Los_Angeles");  // 구글 할당량은 태평양 자정에 초기화

    private final TravelTime travelTime;
    private final Clock clock;
    private final Executor executor;
    private final int dailyLegs;
    private final Duration legTimeout;
    private LocalDate quotaDay;
    private int usedLegs;

    @Autowired
    public CourseTravel(TravelTime travelTime, Clock clock,
                        @Qualifier("applicationTaskExecutor") Executor executor) {
        this(travelTime, clock, executor, DAILY_LEGS, LEG_TIMEOUT);
    }

    CourseTravel(TravelTime travelTime, Clock clock, Executor executor, int dailyLegs, Duration legTimeout) {
        this.travelTime = travelTime;
        this.clock = clock;
        this.executor = executor;
        this.dailyLegs = dailyLegs;
        this.legTimeout = legTimeout;
    }

    public CourseDetailResponse attach(CourseDetailResponse course) {
        ZoneId zone = CityController.findByName(course.city()).map(c -> ZoneId.of(c.timezone())).orElse(null);
        int legCount = course.days().stream().mapToInt(day -> Math.max(day.stops().size() - 1, 0)).sum();
        boolean google = takeQuota(legCount);
        Map<Stop, CompletableFuture<Leg>> legs = new IdentityHashMap<>();
        for (Day day : course.days()) {
            for (int i = 1; i < day.stops().size(); i++) {
                LatLng from = at(day.stops().get(i - 1));
                LatLng to = at(day.stops().get(i));
                Leg estimate = TravelTime.straightLine(from, to);
                if (!google) {
                    legs.put(day.stops().get(i), CompletableFuture.completedFuture(estimate));
                    continue;
                }
                Instant departure = departure(day.stops().get(i - 1), zone);
                legs.put(day.stops().get(i), CompletableFuture.supplyAsync(() -> travelTime.get(from, to, departure), executor)
                        .orTimeout(legTimeout.toMillis(), TimeUnit.MILLISECONDS)   // 시간 초과도 아래 로그에 남게
                        .exceptionally(e -> {
                            log.warn("코스 구간 이동 조회 실패, 직선거리로 추정: {}", e.toString());
                            return estimate;
                        }));
            }
        }
        return course.withTravel(stop -> legs.containsKey(stop) ? legs.get(stop).join() : null);
    }

    // 상한을 넘기는 코스는 통째로 추정 (한 코스 안에서 실제 값과 추정이 섞이지 않게)
    private synchronized boolean takeQuota(int legCount) {
        LocalDate today = LocalDate.now(clock.withZone(QUOTA_ZONE));
        if (!today.equals(quotaDay)) {
            quotaDay = today;
            usedLegs = 0;
        }
        if (usedLegs + legCount > dailyLegs) return false;
        usedLegs += legCount;
        return true;
    }

    private static LatLng at(Stop stop) {
        return new LatLng(stop.latitude(), stop.longitude());
    }

    // 대중교통 시간표용 출발 시각: 내일, 앞 장소의 예시 방문 시각(없으면 10시). 지금 시각이면 밤에 볼 때 대중교통이 없다
    private Instant departure(Stop from, ZoneId zone) {
        if (zone == null) return null;
        LocalTime at = from.visitTime() != null ? from.visitTime() : DEFAULT_DEPART;
        return LocalDate.now(clock.withZone(zone)).plusDays(1).atTime(at).atZone(zone).toInstant();
    }
}
