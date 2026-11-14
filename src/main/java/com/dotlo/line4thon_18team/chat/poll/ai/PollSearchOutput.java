package com.dotlo.line4thon_18team.chat.poll.ai;

import java.util.List;

/**
 * 검색어와 투표 질문.
 *
 * @param question 스키마에서 마지막에 둬서 검색어를 정한 뒤에 쓰게 한다
 */
public record PollSearchOutput(List<String> queries, String question) {

    public PollSearchOutput {
        queries = queries == null ? List.of() : List.copyOf(queries);
        question = question == null ? "" : question.strip();
    }
}
