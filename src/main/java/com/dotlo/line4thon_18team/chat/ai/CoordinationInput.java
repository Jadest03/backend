package com.dotlo.line4thon_18team.chat.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 의견 조율 입력 (COLLAB-COORD-001).
 *
 * <p>참여자는 번호로 넘긴다. 이름을 넘기면 모델이 요약에 이름을 섞어 쓰고, 사용자 id를 넘기면
 * 큰 숫자를 그대로 옮겨 적다가 틀린다. 번호로 받으면 대화에 없는 사람을 지어낸 것도 바로 걸러낼 수 있다.
 *
 * @param lines 대화를 오래된 것부터. 같은 사람이 여러 줄 말하면 같은 번호가 여러 번 나온다
 */
public record CoordinationInput(String city, List<Line> lines) {

    /** @param memberNumber 1부터 시작하는 참여자 번호. 처음 말한 순서대로 붙인다 */
    public record Line(int memberNumber, String text) {
    }

    public CoordinationInput {
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("정리할 대화가 없습니다");
        }
    }

    /** 대화에 실제로 나온 참여자 번호. 여기 없는 번호를 모델이 쓰면 걸러낸다. */
    public Set<Integer> memberNumbers() {
        Set<Integer> numbers = new LinkedHashSet<>();
        lines.forEach(line -> numbers.add(line.memberNumber()));
        return numbers;
    }
}
