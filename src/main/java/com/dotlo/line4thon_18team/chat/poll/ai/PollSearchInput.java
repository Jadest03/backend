package com.dotlo.line4thon_18team.chat.poll.ai;

import java.util.List;

/**
 * 투표 후보를 찾기 위한 검색어 뽑기 입력 (COLLAB-POLL-002).
 *
 * <p>대화는 누가 말했는지 빼고 글만 넘긴다. 여기서 하는 일은 "어떤 장소를 찾을지" 정하는 것뿐이라
 * 누구 말인지가 결과를 바꾸지 않고, 사람을 가리키지 않으니 번호를 매길 이유도 없다.
 * 사람별 정리가 필요한 쪽은 의견 조율이다.
 *
 * @param direction 사용자가 고른 조율 방향. 예: "비용이 적은 대체 장소 찾기"
 */
public record PollSearchInput(String city, String direction, List<String> lines) {

    public PollSearchInput {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
