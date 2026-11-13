package com.dotlo.line4thon_18team.chat.dto;

import java.util.List;

/**
 * 의견 조율 결과. 채팅 메시지의 payload 에 그대로 들어가는 모양이다
 * (type=AI, text + card + options).
 *
 * @param text    말풍선에 나오는 한 문장
 * @param card    사람별 의견 정리 카드
 * @param options 다음에 무엇을 할지 고르는 버튼들. 누르면 그 label 이 사용자 메시지로 전송된다
 */
public record CoordinationResponse(String text, Card card, Options options) {

    public static final String OPINION_SUMMARY = "OPINION_SUMMARY";

    /** @param kind 카드 종류. 지금은 OPINION_SUMMARY 하나다 */
    public record Card(String kind, List<Summary> summaries) {
    }

    /** @param point 그 사람이 원하는 것 한 문장. 화면에는 이름과 함께 나온다 */
    public record Summary(long userId, String point) {
    }

    public record Options(String title, List<Item> items) {
    }

    /** @param id 서버가 붙인 값. 투표 후보를 만들 때 어떤 방향인지 가리키는 데 쓴다 */
    public record Item(String id, String label) {
    }
}
