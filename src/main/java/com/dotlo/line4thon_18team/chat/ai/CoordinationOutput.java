package com.dotlo.line4thon_18team.chat.ai;

import java.util.List;

/**
 * 의견 조율 결과. 참여자는 번호로 돌아오고, 서버가 사용자 id로 되돌린다.
 *
 * @param directions 조율 방향. "조금 더 이야기해 볼게요" 같은 미루기 선택지는 서버가 따로 붙인다
 * @param text       화면 맨 위에 나오는 한 문장. 스키마에서 마지막에 둬서 정리를 끝낸 뒤 쓰게 한다
 */
public record CoordinationOutput(List<Summary> summaries, List<String> directions, String text) {

    public record Summary(int memberNumber, String point) {

        public Summary {
            point = point == null ? "" : point.strip();
        }
    }

    public CoordinationOutput {
        summaries = summaries == null ? List.of() : List.copyOf(summaries);
        directions = directions == null ? List.of() : List.copyOf(directions);
        text = text == null ? "" : text.strip();
    }
}
