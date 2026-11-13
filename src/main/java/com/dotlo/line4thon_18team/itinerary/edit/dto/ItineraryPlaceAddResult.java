package com.dotlo.line4thon_18team.itinerary.edit.dto;

import com.dotlo.line4thon_18team.itinerary.edit.ItineraryDiff;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;

/**
 * 장소 하나를 일정에 넣은 결과. 텍스트 수정과 달리 미리보기를 거치지 않고 바로 저장하므로
 * 저장된 새 버전을 그대로 돌려준다.
 *
 * @param placeName 넣은 장소 이름. 저장 장소에 없던 장소는 구글에서 받은 이름이라 요청에 없다
 * @param reason    AI가 쓴 "어느 날 어디에 넣었는지". 화면에 그대로 보여준다
 * @param itinerary 저장된 새 버전. 최신 일정 조회와 같은 모양이다
 * @param diff      넣기 전 일정과 비교한 변경점. 장소 하나만 넣어도 동선 때문에 순서가 바뀔 수 있다
 */
public record ItineraryPlaceAddResult(String placeName, String reason, ItineraryVersionView itinerary,
                                      ItineraryDiff diff) {
}
