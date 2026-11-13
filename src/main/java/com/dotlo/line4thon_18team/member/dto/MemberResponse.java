package com.dotlo.line4thon_18team.member.dto;

import java.time.LocalDateTime;

/**
 * 여행 구성원 한 명.
 *
 * @param role        OWNER 여행 생성자, MEMBER 초대로 참여한 사람
 * @param characterId 기본 캐릭터 번호 (1~10, 가입 전이면 null)
 * @param me          요청한 사용자 본인인지 ("(나)" 표시)
 * @param joinedAt    참여한 시각 (방장은 여행을 만든 시각)
 */
public record MemberResponse(
        Long userId,
        String name,
        String profileImageUrl,
        Integer characterId,
        Role role,
        boolean me,
        LocalDateTime joinedAt
) {

    public enum Role {
        OWNER,
        MEMBER
    }
}
