package com.dotlo.line4thon_18team.share.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.share.dto.ConfirmResponse;
import com.dotlo.line4thon_18team.share.dto.ShareResponse;
import com.dotlo.line4thon_18team.share.dto.SharedItineraryResponse;
import com.dotlo.line4thon_18team.share.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    // 일정 확정
    @PostMapping("/api/trips/{tripId}/confirm")
    public ResponseEntity<ConfirmResponse> confirm(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(shareService.confirm(tripId, userId));
    }

    // 공유 링크 생성 (확정한 여행만)
    @PostMapping("/api/trips/{tripId}/share")
    public ResponseEntity<ShareResponse> share(@LoginUser Long userId, @PathVariable Long tripId) {
        try {
            return ResponseEntity.ok(shareService.share(tripId, userId));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 눌러 다른 요청이 먼저 링크를 만든 경우. 실패한 트랜잭션이 끝난 뒤 새로 읽는다
            // MySQL(InnoDB)은 뒤 insert 가 앞 트랜잭션이 끝날 때까지 기다렸다가 중복 에러를 받아서, 이 시점엔 앞 토큰이 이미 커밋돼 있다
            return ResponseEntity.ok(shareService.findShare(tripId, userId));
        }
    }

    // 공유된 일정 조회 (로그인 없이)
    @GetMapping("/api/share/{token}")
    public ResponseEntity<SharedItineraryResponse> getShared(@PathVariable String token) {
        return ResponseEntity.ok(shareService.getShared(token));
    }
}
