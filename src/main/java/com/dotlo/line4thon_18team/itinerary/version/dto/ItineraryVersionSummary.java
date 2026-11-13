package com.dotlo.line4thon_18team.itinerary.version.dto;

import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;

import java.time.LocalDateTime;

/**
 * 일정 변경 이력 한 줄.
 *
 * <p>일정 내용(draft)과 검증 결과(validation)는 담지 않는다. 버전이 쌓이면 목록 응답이 일정 몇 벌
 * 분량으로 커지는데, 화면에는 "AI 수정 · 11/11 10:30" 정도만 필요하다. 특정 버전의 내용은
 * 되돌리기로 불러온다.
 *
 * @param baseVersion 어느 버전에서 파생됐는지. AI 전체 생성은 null
 */
public record ItineraryVersionSummary(
        int version,
        ItinerarySource source,
        Integer baseVersion,
        Long createdBy,
        LocalDateTime createdAt
) {
}
