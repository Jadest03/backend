package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.itinerary.ItineraryValidationService.ValidationRun;
import com.dotlo.line4thon_18team.itinerary.ai.PlanEditInput;
import com.dotlo.line4thon_18team.itinerary.ai.PlanEditInput.CurrentDay;
import com.dotlo.line4thon_18team.itinerary.ai.PlanEditInput.CurrentVisit;
import com.dotlo.line4thon_18team.itinerary.ai.PlanEditOutput;
import com.dotlo.line4thon_18team.itinerary.ai.PlanEditTask;
import com.dotlo.line4thon_18team.itinerary.ai.PlanGenerationInput.PlaceRef;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 일정 텍스트 수정 (EDIT-TEXT-001).
 *
 * <pre>
 *   지금 일정 + 사용자 요청 → [AI가 고침] → [검증기가 시각 계산] → 문제 없으면 끝
 *                                 ↑                      ↓ 문제 있으면
 *                                 └── 문제를 알려주고 다시 고침 (최대 2회)
 * </pre>
 *
 * <p>손대지 않은 방문은 itemId가 그대로 유지된다. 그래야 무엇이 바뀌었는지 비교할 수 있고(EDIT-DIFF-001)
 * 저장할 때도 같은 방문으로 이어진다. 체류시간·예약 시각도 지금 일정의 값을 그대로 가져간다.
 *
 * <p>아직 저장하지 않는다. 저장은 사용자가 반영을 누를 때(EDIT-APPLY-001) 한다.
 */
@Service
@RequiredArgsConstructor
public class ItineraryEditService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryEditService.class);

    /** 처음 수정 1회 + 검증에 걸렸을 때 다시 수정 2회 (일정 생성과 같은 횟수) */
    private static final int MAX_ATTEMPTS = 3;

    private final ItineraryValidationService validationService;
    private final StructuredLlmExecutor llm;
    private final PlanEditTask planEditTask;

    /**
     * <p>이 메서드에는 트랜잭션을 걸지 않는다. AI 응답을 기다리는 동안 DB 연결을 쥐고 있지 않기 위해서다.
     * 조회는 {@link ItineraryValidationService#prepare}가 자기 트랜잭션에서 한다.
     *
     * @param current 고치기 전 일정. 일정 저장(BE1)이 생기면 저장된 최신 버전을 넣는다
     * @param request 사용자가 텍스트로 적은 수정 요청
     * @throws BusinessException 남의 여행·없는 여행(404), 요청이 비어 있음(400)
     */
    public EditedItinerary edit(long tripId, long userId, ItineraryDraft current, String request) {
        ValidationRun run = validationService.prepare(tripId, userId);
        LocalDate startDate = run.trip().getStartDate();
        Map<String, VisitDraft> visitsByItemId = indexVisits(current);
        PlanEditInput input = buildInput(run, current, request);

        EditedItinerary last = null;
        List<String> problems = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            PlanEditOutput plan = llm.execute(planEditTask, input.retryWith(problems)).value();
            ItineraryDraft draft = toDraft(plan, input, startDate, visitsByItemId);
            ValidationResult validation = run.validate(draft, ValidationOrigin.AI_GENERATED);

            last = new EditedItinerary(draft, validation, plan.reason(), attempt);
            if (validation.summary().valid()) {
                return last;
            }
            problems = describe(validation, draft, input);
            log.info("일정 재수정 tripId={} attempt={}/{} problems={}", tripId, attempt, MAX_ATTEMPTS, problems);
        }
        // 끝까지 문제가 남으면 마지막 결과를 그대로 돌려준다. 화면에서 경고로 보여주고 사용자가 고치면 된다.
        return last;
    }

    private static Map<String, VisitDraft> indexVisits(ItineraryDraft draft) {
        Map<String, VisitDraft> byItemId = new LinkedHashMap<>();
        draft.days().forEach(day -> day.visits().forEach(visit -> byItemId.put(visit.itemId(), visit)));
        return byItemId;
    }

    /*
     * 지금 일정을 1일차부터 마지막 날까지 채워서 넘긴다. 여행 기간 밖 날짜는 넣지 않는다 (모델이 쓸 수 없는 일차).
     * 추가할 수 있는 장소는 매칭이 끝난 저장 장소 중 지금 일정에 없는 것이다. place ID가 없으면
     * 영업시간·이동시간을 조회할 수 없어서 매칭 전 장소는 넣지 않는다.
     */
    private static PlanEditInput buildInput(ValidationRun run, ItineraryDraft current, String request) {
        Trip trip = run.trip();
        int dayCount = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;

        Map<String, SavedPlace> savedByPlaceId = new LinkedHashMap<>();
        run.places().stream()
                .filter(place -> place.getMatchStatus() == PlaceMatchStatus.MATCHED && place.getPlaceId() != null)
                .forEach(place -> savedByPlaceId.putIfAbsent(place.getPlaceId(), place));

        Map<LocalDate, List<VisitDraft>> byDate = new LinkedHashMap<>();
        current.days().forEach(day -> byDate.put(day.date(), day.visits()));

        List<CurrentDay> currentDays = new ArrayList<>();
        Set<String> inItinerary = new LinkedHashSet<>();
        for (int dayNumber = 1; dayNumber <= dayCount; dayNumber++) {
            LocalDate date = trip.getStartDate().plusDays(dayNumber - 1L);
            List<CurrentVisit> visits = new ArrayList<>();
            for (VisitDraft visit : byDate.getOrDefault(date, List.of())) {
                SavedPlace place = savedByPlaceId.get(visit.placeId());
                // 저장 장소에서 지워진 장소는 이름이 없다. 검증기가 PLACE_INFO_MISSING 으로 잡는다
                visits.add(new CurrentVisit(visit.itemId(), visit.placeId(),
                        place == null ? visit.placeId() : place.getName(),
                        place == null ? null : place.getCategory()));
                inItinerary.add(visit.placeId());
            }
            currentDays.add(new CurrentDay(dayNumber, visits));
        }

        List<PlaceRef> addable = savedByPlaceId.values().stream()
                .filter(place -> !inItinerary.contains(place.getPlaceId()))
                .map(place -> new PlaceRef(place.getPlaceId(), place.getName(), place.getCategory()))
                .toList();

        return PlanEditInput.of(trip.getCity(), dayCount, run.preferences(), currentDays, addable, request);
    }

    /*
     * itemId 로 가리킨 방문은 지금 일정의 값을 그대로 쓴다. 새로 만들면 체류시간과 예약 시각이 날아간다.
     * 모델이 지금 일정에 없는 itemId 를 적으면 PlanEditTask 검증에서 걸러지므로 여기서는 반드시 찾을 수 있다.
     */
    private static ItineraryDraft toDraft(PlanEditOutput plan, PlanEditInput input, LocalDate startDate,
                                          Map<String, VisitDraft> visitsByItemId) {
        List<DayDraft> days = new ArrayList<>();
        for (PlanEditOutput.Day day : plan.days()) {
            LocalDate date = startDate.plusDays(day.dayNumber() - 1L);
            List<VisitDraft> visits = new ArrayList<>();
            for (PlanEditOutput.VisitRef ref : day.visits()) {
                visits.add(ref.isExisting()
                        ? visitsByItemId.get(ref.itemId())
                        : VisitDraft.of(ItineraryItemId.newId(), input.byNumber(ref.placeNumber()).placeId()));
            }
            days.add(new DayDraft(date, visits));
        }
        return new ItineraryDraft(days);
    }

    private static List<String> describe(ValidationResult validation, ItineraryDraft draft, PlanEditInput input) {
        Map<String, String> nameByPlaceId = new LinkedHashMap<>();
        input.currentDays().forEach(day -> day.visits()
                .forEach(visit -> nameByPlaceId.putIfAbsent(visit.placeId(), visit.name())));
        input.addable().forEach(place -> nameByPlaceId.putIfAbsent(place.placeId(), place.name()));
        return ItineraryProblems.describe(validation, draft, nameByPlaceId);
    }
}
