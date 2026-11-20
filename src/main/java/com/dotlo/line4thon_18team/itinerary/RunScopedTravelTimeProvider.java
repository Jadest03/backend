package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.itinerary.validation.port.TravelLookup;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelQuery;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 검증 한 판(ValidationRun) 안에서만 이동시간 조회 결과를 기억한다.
 *
 * <p>일정 생성·수정은 AI가 고친 일정을 최대 3번 검증하는데, 고치지 않은 구간은 출발·도착·수단·출발 시각이
 * 그대로라서 같은 조회가 반복된다. 그 구간은 구글 경로 API를 다시 부르지 않고 이번 요청에서 받은 값을 쓴다.
 *
 * <ul>
 *   <li>요청이 끝나면 이 객체와 함께 버려진다. 요청 사이에는 아무것도 남기지 않는다
 *       (구글 Routes API 약관은 좌표 외 경로 결과의 저장·캐시를 허용하지 않는다).</li>
 *   <li>조회에 성공한 결과(추정치 포함)만 기억한다. 경로 없음·오류는 다시 조회한다.</li>
 * </ul>
 */
public class RunScopedTravelTimeProvider implements TravelTimeProvider {

    private final TravelTimeProvider delegate;
    private final Map<TravelQuery, TravelLookup> lookups = new ConcurrentHashMap<>();

    public RunScopedTravelTimeProvider(TravelTimeProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public TravelLookup lookup(TravelQuery query) {
        TravelLookup known = lookups.get(query);
        if (known != null) {
            return known;
        }
        TravelLookup lookup = delegate.lookup(query);
        if (lookup != null && lookup.status() == TravelLookup.Status.OK) {
            lookups.put(query, lookup);
        }
        return lookup;
    }
}
