package com.dotlo.line4thon_18team.chat.poll.ai;

import java.util.List;

/**
 * 검색 결과 중 투표에 올릴 곳 고르기 입력 (COLLAB-POLL-002, 2/2).
 *
 * <p>장소는 번호로 넘긴다. place ID 는 길고 비슷해서 모델이 옮겨 적다가 틀리기 쉽고,
 * 번호로 받으면 목록에 없는 장소를 만들어 낸 것도 바로 걸러낼 수 있다.
 *
 * @param pickCount 골라야 하는 개수. 검색 결과가 적으면 그 수만큼 줄어든다
 */
public record PollPickInput(String direction, int pickCount, List<Found> places) {

    /** @param number 1부터 시작하는 번호. 모델은 이 번호로만 답한다 */
    public record Found(int number, String name, String address) {
    }

    public PollPickInput {
        places = places == null ? List.of() : List.copyOf(places);
        if (places.isEmpty()) {
            throw new IllegalArgumentException("고를 장소가 없습니다");
        }
        if (pickCount < 1 || pickCount > places.size()) {
            throw new IllegalArgumentException("고를 개수가 범위를 벗어났습니다: " + pickCount);
        }
    }

    public boolean hasNumber(int number) {
        return number >= 1 && number <= places.size();
    }

    public Found byNumber(int number) {
        return places.get(number - 1);
    }
}
