package com.dotlo.line4thon_18team.itinerary.ai;

import com.dotlo.line4thon_18team.itinerary.ai.PlanGenerationInput.Candidate;
import com.dotlo.line4thon_18team.itinerary.ai.PlanGenerationInput.PlaceRef;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * AI 일정 텍스트 수정 입력 (EDIT-TEXT-001).
 *
 * <p>지금 일정은 itemId를 함께 넘긴다. 모델이 그 값을 그대로 돌려주면 손대지 않은 방문이 유지되고,
 * 무엇이 바뀌었는지 비교(EDIT-DIFF-001)도 어긋나지 않는다. 새로 넣을 장소는 일정 생성과 같이
 * 번호로 넘긴다 (place ID는 길고 비슷해서 모델이 옮겨 적다가 틀리기 쉽다).
 *
 * @param currentDays      지금 일정. 1일차부터 dayCount일차까지
 * @param addable          일정에 아직 없는 저장 장소. 번호를 붙여 넘긴다
 * @param request          사용자가 말로 적은 수정 요청
 * @param previousProblems 지난번 수정안이 검증에 걸린 내용. 다시 요청할 때 프롬프트에 붙인다
 */
public record PlanEditInput(String city, int dayCount, PreferenceProfile preferences,
                            List<CurrentDay> currentDays, List<Candidate> addable,
                            String request, List<String> previousProblems) {

    public record CurrentDay(int dayNumber, List<CurrentVisit> visits) {

        public CurrentDay {
            visits = visits == null ? List.of() : List.copyOf(visits);
        }

        public static CurrentDay of(int dayNumber, CurrentVisit... visits) {
            return new CurrentDay(dayNumber, List.of(visits));
        }
    }

    /** 지금 일정에 들어 있는 방문 한 건. */
    public record CurrentVisit(String itemId, String placeId, String name, PlaceCategory category) {

        public CurrentVisit {
            category = category == null ? PlaceCategory.OTHER : category;
        }
    }

    public PlanEditInput {
        Objects.requireNonNull(preferences, "preferences");
        if (dayCount < 1) {
            throw new IllegalArgumentException("여행 일수는 1일 이상이어야 합니다: " + dayCount);
        }
        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("수정 요청이 비어 있습니다");
        }
        currentDays = currentDays == null ? List.of() : List.copyOf(currentDays);
        addable = addable == null ? List.of() : List.copyOf(addable);
        previousProblems = previousProblems == null ? List.of() : List.copyOf(previousProblems);
    }

    /**
     * 추가할 수 있는 장소에 번호를 붙여 만든다. 지금 일정에 이미 있는 장소와 목록 안에서 겹친 장소는 뺀다.
     *
     * <p>이미 있는 장소를 빼는 이유는, 모델이 '그 방문은 그대로 두고' + '같은 장소를 새로 추가'를 함께
     * 내놓으면 같은 곳을 두 번 가는 일정이 되는데 번호만 보는 검증으로는 걸러낼 수 없기 때문이다.
     * 넘겨주는 쪽에서 걸러도 되지만 여기서 막아 두면 어디서 부르든 확실하다.
     */
    public static PlanEditInput of(String city, int dayCount, PreferenceProfile preferences,
                                   List<CurrentDay> currentDays, List<PlaceRef> addable, String request) {
        Set<String> inItinerary = new LinkedHashSet<>();
        if (currentDays != null) {
            currentDays.forEach(day -> day.visits().forEach(visit -> inItinerary.add(visit.placeId())));
        }
        List<Candidate> candidates = new ArrayList<>();
        Set<String> numbered = new LinkedHashSet<>();
        for (PlaceRef place : addable == null ? List.<PlaceRef>of() : addable) {
            if (inItinerary.contains(place.placeId()) || !numbered.add(place.placeId())) {
                continue;
            }
            candidates.add(new Candidate(candidates.size() + 1, place.placeId(), place.name(), place.category()));
        }
        return new PlanEditInput(city, dayCount, preferences, currentDays, candidates, request, List.of());
    }

    /** 검증에 걸린 내용을 붙여 다시 고치게 한다. */
    public PlanEditInput retryWith(List<String> problems) {
        return new PlanEditInput(city, dayCount, preferences, currentDays, addable, request, problems);
    }

    public boolean hasNumber(int number) {
        return number >= 1 && number <= addable.size();
    }

    public Candidate byNumber(int number) {
        return addable.get(number - 1);
    }

    /** 지금 일정에 있는 방문의 itemId. 모델이 없는 값을 적었는지 거를 때 쓴다. */
    public Set<String> itemIds() {
        Set<String> ids = new LinkedHashSet<>();
        currentDays.forEach(day -> day.visits().forEach(visit -> ids.add(visit.itemId())));
        return ids;
    }
}
