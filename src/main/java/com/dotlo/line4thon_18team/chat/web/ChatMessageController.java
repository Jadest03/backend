package com.dotlo.line4thon_18team.chat.web;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.chat.ChatMessageService;
import com.dotlo.line4thon_18team.chat.dto.ChatMessageListResponse;
import com.dotlo.line4thon_18team.chat.dto.ChatMessageResponse;
import com.dotlo.line4thon_18team.chat.dto.ChatMessageSendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trips/{tripId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    // 메시지 보내기. 저장된 메시지를 돌려준다
    @PostMapping
    public ResponseEntity<ChatMessageResponse> send(@LoginUser Long userId, @PathVariable Long tripId,
                                                    @Valid @RequestBody ChatMessageSendRequest request) {
        return ResponseEntity.ok(chatMessageService.send(userId, tripId, request.text()));
    }

    // 메시지 목록. after 없으면 최근 50개, 있으면 그 id 다음 것만 (폴링)
    @GetMapping
    public ResponseEntity<ChatMessageListResponse> list(@LoginUser Long userId, @PathVariable Long tripId,
                                                        @RequestParam(required = false) Long after) {
        return ResponseEntity.ok(chatMessageService.list(userId, tripId, after));
    }
}
