package com.dotlo.line4thon_18team.member.dto;

import java.time.LocalDateTime;

/**
 * @param token     초대 토큰. FE가 /invite/{token} 링크를 만든다
 * @param expiresAt 이 시각이 지나면 만료
 */
public record InviteResponse(String token, LocalDateTime expiresAt) {
}
