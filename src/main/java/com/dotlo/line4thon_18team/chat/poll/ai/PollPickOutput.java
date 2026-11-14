package com.dotlo.line4thon_18team.chat.poll.ai;

import java.util.List;

/** 투표에 올릴 장소. 번호로 돌아오고 서버가 검색 결과로 되돌린다. */
public record PollPickOutput(List<Pick> picks) {

    /** @param tagline 이름 밑에 붙는 유형 한 단어 */
    public record Pick(int number, String tagline) {

        public Pick {
            tagline = tagline == null ? "" : tagline.strip();
        }
    }

    public PollPickOutput {
        picks = picks == null ? List.of() : List.copyOf(picks);
    }
}
