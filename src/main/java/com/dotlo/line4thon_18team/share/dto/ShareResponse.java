package com.dotlo.line4thon_18team.share.dto;

/**
 * @param token 공유 링크 토큰. 공유된 일정 조회 GET /api/share/{token} 에 쓴다
 */
public record ShareResponse(String token) {
}
