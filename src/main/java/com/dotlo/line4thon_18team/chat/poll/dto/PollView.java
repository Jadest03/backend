package com.dotlo.line4thon_18team.chat.poll.dto;

import com.dotlo.line4thon_18team.chat.poll.entity.PollStatus;

import java.util.List;

/**
 * 채팅방 투표 카드에 그릴 값. 채팅 메시지 조회(BE2)가 이 모양 그대로 함께 내려준다.
 *
 * @param totalMembers 지금 여행 구성원 수 (방장 포함). "3명 중 2명 투표"의 3
 * @param votedCount   투표한 사람 수. 한 사람이 여러 번 눌러도 1이다
 */
public record PollView(long pollId, String question, PollStatus status, long totalMembers, int votedCount,
                       List<Candidate> candidates) {

    /**
     * @param tagline      이름 밑 짧은 설명. 없을 수 있다
     * @param voterUserIds 이 후보를 고른 사람. 화면에 아바타로 나온다
     */
    public record Candidate(long id, String placeId, String name, String tagline, int votes,
                            List<Long> voterUserIds) {
    }
}
