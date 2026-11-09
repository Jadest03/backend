package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.Severity;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationIssue;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검증에 걸린 내용을 모델이 읽을 문장으로 바꾼다. 일정 생성과 일정 수정이 같이 쓴다.
 *
 * <p>오류만 넘긴다. 경고(영업시간 미확인, 이동시간 추정 등)는 다시 배치해도 없어지지 않아서,
 * 넘기면 모델이 고칠 수 없는 문제로 계속 재시도하게 된다.
 */
final class ItineraryProblems {

    private ItineraryProblems() {
    }

    /**
     * @param nameByPlaceId place ID → 장소 이름. 모델에게 번호가 아니라 이름으로 알려주기 위한 것
     */
    static List<String> describe(ValidationResult validation, ItineraryDraft draft,
                                 Map<String, String> nameByPlaceId) {
        Map<String, String> placeIdByItemId = new LinkedHashMap<>();
        draft.days().forEach(day -> day.visits()
                .forEach(visit -> placeIdByItemId.put(visit.itemId(), visit.placeId())));

        List<String> problems = new ArrayList<>();
        for (ValidationIssue issue : validation.issues()) {
            if (issue.severity() != Severity.ERROR) {
                continue;
            }
            String placeId = issue.placeId() != null ? issue.placeId() : placeIdByItemId.get(issue.itemId());
            String name = nameByPlaceId.getOrDefault(placeId, "(장소 미상)");
            problems.add(issue.date() + " " + name + ": " + reason(issue));
        }
        return problems;
    }

    private static String reason(ValidationIssue issue) {
        return switch (issue.code()) {
            case OUTSIDE_OPENING_HOURS -> "영업시간에 맞지 않습니다 (도착 " + issue.params().get("arrival")
                    + ", 영업 " + issue.params().get("openingHours") + ")";
            case CLOSED_ON_DAY -> "그날 휴무입니다";
            case FIXED_TIME_MISSED -> "예약 시각보다 늦게 도착합니다";
            case LONG_WAIT -> "문 열 때까지 " + issue.params().get("waitMinutes") + "분을 기다려야 합니다";
            case DAY_OVERFLOW -> "그날 일정이 " + issue.params().get("overflowMinutes")
                    + "분 넘칩니다. 장소를 줄이거나 다른 날로 옮기세요";
            default -> issue.code().name();
        };
    }
}
