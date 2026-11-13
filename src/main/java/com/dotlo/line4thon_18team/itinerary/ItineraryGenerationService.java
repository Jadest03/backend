package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.itinerary.ItineraryValidationService.ValidationRun;
import com.dotlo.line4thon_18team.itinerary.ai.PlanGenerationInput;
import com.dotlo.line4thon_18team.itinerary.ai.PlanGenerationOutput;
import com.dotlo.line4thon_18team.itinerary.ai.PlanGenerationTask;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
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
import java.util.List;
import java.util.Map;

/**
 * AI 일정 생성 (GEN-PLAN-001).
 *
 * <pre>
 *   저장 장소 → [AI가 일차별로 배치] → [검증기가 시각 계산] → 문제 없으면 끝
 *                      ↑                        ↓ 문제 있으면
 *                      └── 문제를 알려주고 다시 배치 (최대 2회)
 * </pre>
 *
 * <p>AI는 "어떤 장소를 며칠째 몇 번째로" 까지만 정하고, 시각은 검증기가 이동시간·영업시간으로 계산한다.
 */
@Service
@RequiredArgsConstructor

public class ItineraryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationService.class);

    /** 처음 배치 1회 + 검증에 걸렸을 때 다시 배치 2회 (명세서: 내부에서 검증/최대 2회 재생성) */
    private static final int MAX_ATTEMPTS = 3;

    private final ItineraryValidationService validationService;
    private final StructuredLlmExecutor llm;
    private final PlanGenerationTask planGenerationTask;
    private final ItineraryService itineraryService;   // BE1 일정 저장

    /**
     * 일정을 만들어 새 버전으로 저장한다 (GEN-PLAN-001). API에서 부르는 진입점이다.
     *
     * <p>저장은 AI 응답을 다 받은 뒤 한 번만 일어난다. 중간에 실패하면 아무것도 저장되지 않는다.
     *
     * @throws BusinessException 남의 여행·없는 여행(404), 배치할 장소가 없음(400), 숙소를 일부만 넣음(400)
     */
    public GeneratedItineraryVersion generateAndSave(long tripId, long userId) {
        GeneratedItinerary generated = generate(tripId, userId);
        ItineraryVersionView saved = itineraryService.save(tripId, userId, ItinerarySource.AI_GENERATED, null,
                generated.draft(), generated.validation());
        return new GeneratedItineraryVersion(saved, generated.excludedPlaceIds());
    }

    /**
     * 일정을 만들기만 하고 저장하지 않는다. 저장까지 하려면 {@link #generateAndSave}를 쓴다.
     *
     * <p>이 메서드에는 트랜잭션을 걸지 않는다. AI 응답을 기다리는 동안 DB 연결을 쥐고 있지 않기 위해서다.
     * 조회는 {@link ItineraryValidationService#prepare}가 자기 트랜잭션에서 하고, 돌려받은 엔티티는
     * 기본 필드만 읽는다 (연관 관계를 더 타면 트랜잭션이 이미 닫혀 있다).
     * @throws BusinessException 남의 여행·없는 여행(404), 배치할 장소가 없음(400)
     */
    public GeneratedItinerary generate(long tripId, long userId) {
        ValidationRun run = validationService.prepare(tripId, userId);
        PlanGenerationInput input = buildInput(run);

        GeneratedItinerary last = null;
        List<String> problems = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            PlanGenerationOutput plan = llm.execute(planGenerationTask, input.retryWith(problems)).value();
            ItineraryDraft draft = toDraft(plan, input, run.trip().getStartDate());
            ValidationResult validation = run.validate(draft, ValidationOrigin.AI_GENERATED);

            last = new GeneratedItinerary(draft, validation, placeIds(plan.excluded(), input), attempt);
            if (validation.summary().valid()) {
                return last;
            }
            problems = describe(validation, draft, input);
            log.info("일정 재생성 tripId={} attempt={}/{} problems={}", tripId, attempt, MAX_ATTEMPTS, problems);
        }
        // 끝까지 문제가 남으면 마지막 결과를 그대로 돌려준다. 화면에서 경고로 보여주고 사용자가 고치면 된다.
        return last;
    }

    /** 매칭이 끝난 장소만 쓴다. place ID가 없으면 영업시간·이동시간을 조회할 수 없다. */
    private static PlanGenerationInput buildInput(ValidationRun run) {
        List<PlanGenerationInput.PlaceRef> candidates = run.places().stream()
                .filter(place -> place.getMatchStatus() == PlaceMatchStatus.MATCHED && place.getPlaceId() != null)
                .map(place -> new PlanGenerationInput.PlaceRef(place.getPlaceId(), place.getName(),
                        place.getCategory()))
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_PLACES_TO_PLAN);
        }
        Trip trip = run.trip();
        int dayCount = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        return PlanGenerationInput.of(trip.getCity(), dayCount, trip.getCompanion(), run.preferences(), candidates);
    }

    /** 장소 번호를 place ID로 되돌리고 itemId를 새로 만든다. */
    private static ItineraryDraft toDraft(PlanGenerationOutput plan, PlanGenerationInput input, LocalDate startDate) {
        List<DayDraft> days = new ArrayList<>();
        for (PlanGenerationOutput.Day day : plan.days()) {
            LocalDate date = startDate.plusDays(day.dayNumber() - 1L);
            List<VisitDraft> visits = day.placeNumbers().stream()
                    .map(number -> VisitDraft.of(ItineraryItemId.newId(), input.byNumber(number).placeId()))
                    .toList();
            days.add(new DayDraft(date, visits));
        }
        return new ItineraryDraft(days);
    }

    private static List<String> placeIds(List<Integer> numbers, PlanGenerationInput input) {
        return numbers.stream().map(number -> input.byNumber(number).placeId()).toList();
    }

    private static List<String> describe(ValidationResult validation, ItineraryDraft draft,
                                         PlanGenerationInput input) {
        Map<String, String> nameByPlaceId = new LinkedHashMap<>();
        input.places().forEach(place -> nameByPlaceId.put(place.placeId(), place.name()));
        return ItineraryProblems.describe(validation, draft, nameByPlaceId);
    }
}
