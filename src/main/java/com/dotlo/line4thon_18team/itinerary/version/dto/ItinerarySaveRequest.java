package com.dotlo.line4thon_18team.itinerary.version.dto;

import com.dotlo.line4thon_18team.itinerary.ItineraryItemId;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 사용자가 직접 고친 일정 저장 요청. 시각은 받지 않는다 (서버가 검증하며 계산).
 *
 * @param baseVersion 고치기 전 버전 (최신 일정 조회의 version). 처음 저장이면 null
 */
public record ItinerarySaveRequest(
        Integer baseVersion,
        @NotNull(message = "days는 필수입니다.")
        List<@Valid @NotNull Day> days
) {

    public record Day(
            @NotNull(message = "date는 필수입니다.")
            LocalDate date,
            @NotNull(message = "visits는 필수입니다.")
            List<@Valid @NotNull Visit> visits
    ) {
    }

    /** @param itemId 기존 방문은 그대로, 새 방문은 비워 두면 서버가 만든다 */
    public record Visit(
            String itemId,
            @NotBlank(message = "placeId는 필수입니다.")
            String placeId,
            @Min(value = 1, message = "stayMinutes는 1~720분이어야 합니다.")
            @Max(value = 720, message = "stayMinutes는 1~720분이어야 합니다.")
            Integer stayMinutes,
            LocalTime fixedStart
    ) {
    }

    public ItineraryDraft toDraft() {
        return new ItineraryDraft(days.stream()
                .map(day -> new DayDraft(day.date(), day.visits().stream()
                        .map(visit -> new VisitDraft(
                                visit.itemId() == null || visit.itemId().isBlank() ? ItineraryItemId.newId() : visit.itemId(),
                                visit.placeId(), visit.stayMinutes(), visit.fixedStart()))
                        .toList()))
                .toList());
    }
}
