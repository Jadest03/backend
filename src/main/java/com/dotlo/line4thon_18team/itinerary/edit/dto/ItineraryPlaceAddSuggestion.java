package com.dotlo.line4thon_18team.itinerary.edit.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 장소를 어느 날에 넣을지 고르는 화면에 쓰는 값 ("오사카성 공원, 언제 넣을까요?").
 * 아직 일정을 바꾸지 않는다. 사용자가 날을 고르면 장소 넣기를 다시 부른다.
 *
 * @param recommendedDay 동선이 가장 자연스러운 날. 좌표를 모르면 null (그때는 사용자가 그냥 고른다)
 * @param days           1일차부터 마지막 날까지 전부. 여행 기간이 짧아도 빠진 날 없이 내려준다
 */
public record ItineraryPlaceAddSuggestion(String placeId, String placeName, Integer recommendedDay,
                                          List<Day> days) {

    /**
     * @param visitCount 그 날에 이미 들어 있는 장소 수. "이 날은 이미 5곳"처럼 안내할 때 쓴다
     * @param recommended 추천하는 날이면 true. 목록에 많아야 하나다
     */
    public record Day(int day, LocalDate date, int visitCount, boolean recommended) {
    }
}
