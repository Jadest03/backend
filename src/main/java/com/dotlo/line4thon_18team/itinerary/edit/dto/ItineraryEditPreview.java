package com.dotlo.line4thon_18team.itinerary.edit.dto;

import com.dotlo.line4thon_18team.itinerary.edit.ItineraryDiff;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;

/**
 * AI가 만든 수정안 (EDIT-TEXT-001). 아직 일정으로 저장되지 않았다.
 *
 * @param baseVersion 이 수정안이 고친 버전. 적용할 때 {@code {version}} 자리에 이 값을 보낸다
 * @param reason      AI가 쓴 "무엇을 어떻게 고쳤는지". 화면에 그대로 보여준다
 * @param itinerary   고친 일정과 계산된 시각. 저장된 버전이 아니라서 version·source·createdAt은 없다
 * @param diff        고치기 전 일정과 비교한 변경점 (EDIT-DIFF-001)
 */
public record ItineraryEditPreview(int baseVersion, String reason, Itinerary itinerary, ItineraryDiff diff) {

    /**
     * @param draft      일차별 방문 순서. 시각은 없다
     * @param validation 계산된 시각과 문제 목록. 화면에 그릴 시각은 여기 days 에 있다
     */
    public record Itinerary(ItineraryDraft draft, ValidationResult validation) {
    }
}
