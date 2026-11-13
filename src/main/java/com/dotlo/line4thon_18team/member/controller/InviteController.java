package com.dotlo.line4thon_18team.member.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.member.dto.InvitePreviewResponse;
import com.dotlo.line4thon_18team.member.dto.InviteResponse;
import com.dotlo.line4thon_18team.member.dto.MemberResponse;
import com.dotlo.line4thon_18team.member.service.InviteService;
import com.dotlo.line4thon_18team.member.service.TripLeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final TripLeaveService tripLeaveService;

    // 초대 링크 생성 (구성원 누구나)
    @PostMapping("/api/trips/{tripId}/invites")
    public ResponseEntity<InviteResponse> createInvite(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(inviteService.createInvite(tripId, userId));
    }

    // 초대 미리보기 (로그인 없이)
    @GetMapping("/api/invites/{token}")
    public ResponseEntity<InvitePreviewResponse> preview(@PathVariable String token) {
        return ResponseEntity.ok(inviteService.preview(token));
    }

    // 초대 수락 → 들어갈 여행 id
    @PostMapping("/api/invites/{token}/accept")
    public ResponseEntity<Map<String, Long>> accept(@LoginUser Long userId, @PathVariable String token) {
        Long tripId;
        try {
            tripId = inviteService.accept(token, userId);
        } catch (DataIntegrityViolationException e) {
            // 수락을 동시에 두 번 눌러 먼저 들어간 요청이 구성원을 만든 경우. 다시 부르면 "이미 구성원"으로 통과한다
            tripId = inviteService.accept(token, userId);
        }
        return ResponseEntity.ok(Map.of("tripId", tripId));
    }

    // 구성원 목록 (방장 + 참여한 사람)
    @GetMapping("/api/trips/{tripId}/members")
    public ResponseEntity<List<MemberResponse>> getMembers(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(inviteService.getMembers(tripId, userId));
    }

    // 여행 나가기. 방장이면 가장 먼저 참여한 멤버에게 넘기고, 혼자면 여행이 지워진다
    @DeleteMapping("/api/trips/{tripId}/members/me")
    public ResponseEntity<Map<String, Boolean>> leave(@LoginUser Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(Map.of("tripDeleted", tripLeaveService.leave(tripId, userId)));
    }
}
