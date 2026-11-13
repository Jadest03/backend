package com.dotlo.line4thon_18team.member.dto;

import com.dotlo.line4thon_18team.trip.entity.Companion;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 초대받은 사람이 참여 전에 보는 여행 정보 (로그인 없이 볼 수 있어서 이메일 같은 개인정보는 넣지 않는다).
 *
 * @param memberCount 지금 참여 인원 (방장 포함)
 */
public record InvitePreviewResponse(
        String city,
        LocalDate startDate,
        LocalDate endDate,
        Companion companion,
        String inviterName,
        int memberCount,
        int maxMembers,
        long savedPlaceCount,
        LocalDateTime expiresAt
) {
}
