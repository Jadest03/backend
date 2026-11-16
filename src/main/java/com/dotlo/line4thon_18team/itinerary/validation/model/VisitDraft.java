package com.dotlo.line4thon_18team.itinerary.validation.model;

import java.time.LocalTime;

/**
 * 검증할 일정 안의 방문 한 건. 시각은 넣지 않는다: 검증기가 이동시간·영업시간으로 직접 계산한다
 * (AI가 시간을 지어내지 않도록).
 *
 * @param itemId      일정 항목 ID (같은 장소를 두 번 넣을 수 있어서 placeId와 따로 둔다)
 * @param placeId     장소 ID (Places API place ID)
 * @param stayMinutes 체류시간(분). null이면 장소 유형과 일정 강도로 기본값을 쓴다.
 * @param fixedStart  예약 등으로 시작 시각이 정해진 경우. null이면 도착하는 대로(영업 시작 후) 시작한다.
 * @param travelMode  이 장소까지 오는 구간의 수단 (사용자가 고른 값). null이면 검증기가 거리로 정한다.
 */
public record VisitDraft(String itemId, String placeId, Integer stayMinutes, LocalTime fixedStart,
                         TravelMode travelMode) {

    public VisitDraft {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId가 비어 있습니다");
        }
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("placeId가 비어 있습니다");
        }
        if (stayMinutes != null && (stayMinutes < 1 || stayMinutes > 720)) {
            throw new IllegalArgumentException("stayMinutes는 1~720분이어야 합니다: " + stayMinutes);
        }
    }

    /** 수단을 고르지 않은 방문. */
    public VisitDraft(String itemId, String placeId, Integer stayMinutes, LocalTime fixedStart) {
        this(itemId, placeId, stayMinutes, fixedStart, null);
    }

    public static VisitDraft of(String itemId, String placeId) {
        return new VisitDraft(itemId, placeId, null, null);
    }

    public VisitDraft withStayMinutes(int minutes) {
        return new VisitDraft(itemId, placeId, minutes, fixedStart, travelMode);
    }

    public VisitDraft withFixedStart(LocalTime time) {
        return new VisitDraft(itemId, placeId, stayMinutes, time, travelMode);
    }
}
