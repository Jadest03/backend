package com.dotlo.line4thon_18team.notification.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.notification.NotificationService;
import com.dotlo.line4thon_18team.notification.dto.NotificationListResponse;
import com.dotlo.line4thon_18team.notification.dto.UnreadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    // 내 알림 최근 50개 (최신순). 오늘/이전 구분은 createdAt 으로
    @GetMapping
    public ResponseEntity<NotificationListResponse> list(@LoginUser Long userId) {
        return ResponseEntity.ok(notificationService.list(userId));
    }

    // 홈 종 아이콘 점: 안 읽은 알림이 있는지
    @GetMapping("/unread")
    public ResponseEntity<UnreadResponse> unread(@LoginUser Long userId) {
        return ResponseEntity.ok(notificationService.unread(userId));
    }

    // 알림 하나 읽음 → 204
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> read(@LoginUser Long userId, @PathVariable Long notificationId) {
        notificationService.read(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    // 모두 읽음 → 204
    @PatchMapping("/read-all")
    public ResponseEntity<Void> readAll(@LoginUser Long userId) {
        notificationService.readAll(userId);
        return ResponseEntity.noContent().build();
    }
}
