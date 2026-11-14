package com.dotlo.line4thon_18team.chat.web;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.chat.poll.PollService;
import com.dotlo.line4thon_18team.chat.poll.dto.PollView;
import com.dotlo.line4thon_18team.chat.poll.dto.VoteCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 채팅방 투표 API (BE3). 로그인이 필요하고 여행 구성원이 아니면 404다.
 *
 * <p>투표를 만드는 API 는 없다. 화면에서 투표가 생기는 길이 @도틀로 투표 만들기 하나뿐이라
 * 그쪽 AI API 가 만든다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/chat/polls")
@RequiredArgsConstructor
public class ChatPollController {

    private final PollService pollService;

    /**
     * 후보 하나에 투표한다. 이미 투표했으면 그 표를 옮긴다. 구성원이 모두 투표하면 그 자리에서 마감된다.
     *
     * <p>없는 투표면 404, 이미 끝난 투표면 409, 그 투표의 후보가 아니면 400이다.
     */
    @PostMapping("/{pollId}/votes")
    public PollView vote(@PathVariable("tripId") long tripId, @PathVariable("pollId") long pollId,
                         @LoginUser Long userId, @Valid @RequestBody VoteCommand command) {
        return pollService.vote(tripId, userId, pollId, command.candidateId());
    }

    /** 이 여행의 최근 투표 상태. 채팅 메시지 조회(BE2)가 같은 값을 함께 내려주므로 보조 수단이다. */
    @GetMapping
    public List<PollView> recentPolls(@PathVariable("tripId") long tripId, @LoginUser Long userId) {
        return pollService.recentPolls(tripId, userId);
    }
}
