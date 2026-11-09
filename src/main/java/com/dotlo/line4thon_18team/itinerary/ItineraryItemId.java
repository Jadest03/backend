package com.dotlo.line4thon_18team.itinerary;

import java.util.UUID;

/**
 * 일정 항목 ID를 만든다. 방문 한 건을 가리키는 값으로, 같은 장소를 하루에 두 번 넣을 수 있어서
 * placeId와 따로 둔다.
 *
 * <p>AI 일정 생성·수정(BE3)과 사용자가 직접 추가한 방문 저장(BE1)이 모두 이 함수로 만든다.
 * 형식이 갈리거나 저장할 때 새로 매기면, 일정을 고칠 때 무엇이 바뀌었는지 비교(EDIT-DIFF-001)가
 * 어긋난다. DB가 매기는 번호를 쓰지 않는 이유도 같다.
 */
public final class ItineraryItemId {

    private static final String PREFIX = "v-";

    private ItineraryItemId() {
    }

    /** 예: v-3f9a1c20 */
    public static String newId() {
        return PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }
}
