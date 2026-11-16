package com.dotlo.line4thon_18team.itinerary.web;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.TravelMode;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
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
 * 일정 검증 요청 (POST /api/trips/{tripId}/itineraries/check).
 * 시각은 보내지 않는다. 방문 순서만 보내면 검증기가 이동시간·영업시간으로 계산한다.
 *
 * @param origin AI가 만든 일정인지, 사용자가 직접 고친 일정인지 (일정 충돌을 오류로 볼지 경고로 볼지가 달라진다)
 */
public record ItineraryCheckRequest(

        @NotNull(message = "origin은 필수입니다. (AI_GENERATED 또는 USER_EDITED)")
        ValidationOrigin origin,

        @NotNull(message = "days는 필수입니다.")
        List<@Valid @NotNull Day> days
) {

    /**
     * @param stayMinutes 체류시간(분). 없으면 장소 유형과 일정 강도로 기본값을 쓴다.
     * @param fixedStart  예약 등으로 시작 시각이 정해진 경우 ("HH:mm")
     * @param travelMode  이 장소까지 오는 구간의 수단 (WALK, TRANSIT, DRIVE). 없으면 자동
     */
    public record Visit(

            @NotBlank(message = "itemId는 필수입니다.")
            String itemId,

            @NotBlank(message = "placeId는 필수입니다.")
            String placeId,

            @Min(value = 1, message = "stayMinutes는 1분 이상이어야 합니다.")
            @Max(value = 720, message = "stayMinutes는 720분 이하여야 합니다.")
            Integer stayMinutes,

            LocalTime fixedStart,

            TravelMode travelMode
    ) {
    }

    public record Day(

            @NotNull(message = "date는 필수입니다.")
            LocalDate date,

            @NotNull(message = "visits는 필수입니다. 갈 곳이 없는 날은 빈 배열로 보내세요.")
            List<@Valid @NotNull Visit> visits
    ) {
    }

    public ItineraryDraft toDraft() {
        return new ItineraryDraft(days.stream().map(ItineraryCheckRequest::toDayDraft).toList());
    }

    private static DayDraft toDayDraft(Day day) {
        return new DayDraft(day.date(), day.visits().stream()
                .map(v -> new VisitDraft(v.itemId(), v.placeId(), v.stayMinutes(), v.fixedStart(), v.travelMode()))
                .toList());
    }
}
