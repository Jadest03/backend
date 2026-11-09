package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;

/**
 * AI가 고친 일정 (EDIT-TEXT-001). 아직 저장하지 않은 상태다.
 *
 * <p>무엇이 바뀌었는지는 담지 않는다. 변경점은 {@code ItineraryDiff.between(이전, draft)}로 그때 계산한다.
 * 수정과 변경점 보기가 따로 불릴 수 있어서, 서비스는 고친 일정까지만 돌려준다.
 *
 * @param draft      고친 일차별 방문 순서. 손대지 않은 방문은 itemId가 그대로다
 * @param validation 계산된 시각과 문제 목록
 * @param attempts   수정 시도 횟수 (1이면 처음에 바로 통과)
 */
public record EditedItinerary(ItineraryDraft draft, ValidationResult validation, int attempts) {
}
