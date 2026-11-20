package com.dotlo.line4thon_18team.course.service;

import com.dotlo.line4thon_18team.city.CityController;
import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse;
import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse.Day;
import com.dotlo.line4thon_18team.course.dto.CourseDetailResponse.Stop;
import com.dotlo.line4thon_18team.place.PlaceMatcher.LatLng;
import com.dotlo.line4thon_18team.place.TravelTime;
import com.dotlo.line4thon_18team.place.TravelTime.Leg;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 공개 코스 상세의 구간 이동 (같은 날 앞 장소 → 이 장소). 구글 약관상 경로 결과는 저장할 수 없어 볼 때마다 조회한다.
 * 구간당 구글 경로 조회 최대 3번 (코스 하나 구간 3~9개). 구글 오류면 TravelTime 이 직선거리로 추정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseTravel {

    private static final LocalTime DEFAULT_DEPART = LocalTime.of(10, 0);
    // ponytail: 서버 전체 8개 동시 조회. 코스 하나 구간이 최대 9개라 한 번에 거의 다 보냄. 느려지면 늘린다
    private static final ExecutorService POOL = Executors.newFixedThreadPool(8);

    private final TravelTime travelTime;
    private final Clock clock;

    public CourseDetailResponse attach(CourseDetailResponse course) {
        ZoneId zone = CityController.findByName(course.city()).map(c -> ZoneId.of(c.timezone())).orElse(null);
        Map<Long, CompletableFuture<Leg>> legs = new HashMap<>();
        for (Day day : course.days()) {
            for (int i = 1; i < day.stops().size(); i++) {
                Stop from = day.stops().get(i - 1);
                Stop to = day.stops().get(i);
                Instant departure = departure(from, zone);
                legs.put(to.id(), CompletableFuture.supplyAsync(() -> travelTime.get(
                        new LatLng(from.latitude(), from.longitude()), new LatLng(to.latitude(), to.longitude()),
                        departure), POOL).exceptionally(e -> {
                    log.warn("코스 구간 이동 조회 실패 stopId={}: {}", to.id(), e.getMessage());
                    return null;   // 한 구간이 실패해도 상세는 보여준다
                }));
            }
        }
        return course.withTravel(id -> legs.containsKey(id) ? legs.get(id).join() : null);
    }

    // 대중교통 시간표용 출발 시각: 내일, 앞 장소의 예시 방문 시각(없으면 10시). 지금 시각이면 밤에 볼 때 대중교통이 없다
    private Instant departure(Stop from, ZoneId zone) {
        if (zone == null) return null;
        LocalTime at = from.visitTime() != null ? from.visitTime() : DEFAULT_DEPART;
        return LocalDate.now(clock.withZone(zone)).plusDays(1).atTime(at).atZone(zone).toInstant();
    }
}
