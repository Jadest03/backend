package com.dotlo.line4thon_18team.itinerary.version.dto;

import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;

/**
 * 저장된 일정 한 버전.
 *
 * @param draft      일차별 방문 순서. 시각은 없다
 * @param validation 검증 결과 JSON 문자열. 화면에 그릴 시각은 여기 days 에 있다 (응답에는 JSON 객체로 나간다)
 */
public record ItineraryVersionView(
        int version,
        ItinerarySource source,
        Integer baseVersion,
        Long createdBy,
        LocalDateTime createdAt,
        ItineraryDraft draft,
        @JsonRawValue String validation
) {
}
