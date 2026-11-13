package com.dotlo.line4thon_18team.itinerary.ai;

import java.util.List;

/**
 * AI 일정 텍스트 수정 출력. 필드는 plan-edit.schema.json과 같다.
 *
 * <p>고친 일정 전체를 다시 받는다. 지금 일정에 있던 방문은 itemId로 가리키고 새로 넣는 장소만
 * 번호로 받으므로, 손대지 않은 방문은 itemId가 그대로 유지된다. days에서 빠진 방문이 삭제된
 * 방문이다. 일정 생성과 달리 '넣지 못한 장소' 목록은 두지 않는다 (빠진 것이 곧 삭제다).
 *
 * @param days   일차별 방문 (방문 순서대로)
 * @param reason 무엇을 어떻게 고쳤는지 한두 문장. 사용자에게 그대로 보여 준다. 고친 일정을 먼저 정한 뒤
 *               쓰도록 스키마에서 days 다음에 둔다. 먼저 쓰게 하면 이유에 맞춰 일정을 짜게 된다
 */
public record PlanEditOutput(List<Day> days, String reason) {

    public record Day(int dayNumber, List<VisitRef> visits) {

        public Day {
            visits = visits == null ? List.of() : List.copyOf(visits);
        }
    }

    /**
     * 방문 한 건. 둘 중 하나만 채운다.
     *
     * @param itemId      지금 일정에 있던 방문을 그대로 둘 때 그 itemId. 새로 넣는 장소면 null
     * @param placeNumber 새로 넣는 장소 번호. 지금 일정에 있던 방문이면 null
     */
    public record VisitRef(String itemId, Integer placeNumber) {

        public boolean isExisting() {
            return itemId != null;
        }

        public boolean isNew() {
            return placeNumber != null;
        }
    }

    public PlanEditOutput {
        days = days == null ? List.of() : List.copyOf(days);
        reason = reason == null ? "" : reason.strip();
    }
}
