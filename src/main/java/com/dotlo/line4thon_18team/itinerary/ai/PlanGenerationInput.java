package com.dotlo.line4thon_18team.itinerary.ai;

import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.trip.entity.Companion;

import java.util.List;
import java.util.Objects;

/**
 * AI 일정 생성 입력 (GEN-PLAN-001).
 *
 * <p>장소는 번호를 붙여서 넘긴다. place ID는 길고 비슷해서 모델이 그대로 옮겨 적다가 틀리기 쉬운데,
 * 번호로 받으면 입력에 없는 장소를 만들어 내는 것도 바로 걸러낼 수 있다.
 *
 * @param dayCount         여행 일수 (1일차 ~ dayCount일차)
 * @param companion        누구와 떠나는지. 여행 만들 때 고른 값을 배치에 참고한다 (취향 칩과는 별개)
 * @param previousProblems 지난번 배치에서 검증에 걸린 내용. 다시 배치할 때 프롬프트에 붙인다.
 */
public record PlanGenerationInput(String city, int dayCount, Companion companion, PreferenceProfile preferences,
                                  List<Candidate> places, List<String> previousProblems) {

    /**
     * @param number   1부터 시작하는 장소 번호. 모델은 이 번호로만 답한다.
     * @param category 장소 유형. 보강 전이거나 매칭에 실패한 장소는 비어 있어서 OTHER로 채운다.
     */
    public record Candidate(int number, String placeId, String name, PlaceCategory category) {

        public Candidate {
            category = category == null ? PlaceCategory.OTHER : category;
        }
    }

    public PlanGenerationInput {
        Objects.requireNonNull(preferences, "preferences");
        if (dayCount < 1) {
            throw new IllegalArgumentException("여행 일수는 1일 이상이어야 합니다: " + dayCount);
        }
        places = places == null ? List.of() : List.copyOf(places);
        if (places.isEmpty()) {
            throw new IllegalArgumentException("배치할 장소가 없습니다");
        }
        previousProblems = previousProblems == null ? List.of() : List.copyOf(previousProblems);
    }

    /** 장소 목록에 번호를 붙여 만든다. */
    public static PlanGenerationInput of(String city, int dayCount, Companion companion,
                                         PreferenceProfile preferences, List<PlaceRef> places) {
        List<Candidate> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            PlaceRef place = places.get(i);
            candidates.add(new Candidate(i + 1, place.placeId(), place.name(), place.category()));
        }
        return new PlanGenerationInput(city, dayCount, companion, preferences, candidates, List.of());
    }

    /** 검증에 걸린 내용을 붙여 다시 배치하게 한다. */
    public PlanGenerationInput retryWith(List<String> problems) {
        return new PlanGenerationInput(city, dayCount, companion, preferences, places, problems);
    }

    /** 번호를 붙이기 전의 장소. category가 비어 있으면 OTHER로 본다. */
    public record PlaceRef(String placeId, String name, PlaceCategory category) {

        public PlaceRef {
            category = category == null ? PlaceCategory.OTHER : category;
        }
    }

    public Candidate byNumber(int number) {
        return places.get(number - 1);
    }

    public boolean hasNumber(int number) {
        return number >= 1 && number <= places.size();
    }
}
