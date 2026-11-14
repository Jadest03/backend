package com.dotlo.line4thon_18team.chat.web;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.chat.poll.PollCandidateService;
import com.dotlo.line4thon_18team.chat.poll.dto.PollCandidateCommand;
import com.dotlo.line4thon_18team.chat.poll.dto.PollView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 투표 후보 만들기 (BE3). 로그인이 필요하고 여행 구성원이 아니면 404다.
 * 투표하기와 조회는 {@link ChatPollController} 쪽이다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/chat/poll-candidates")
@RequiredArgsConstructor
public class ChatPollCandidateController {

    private final PollCandidateService pollCandidateService;

    /**
     * 고른 조율 방향에 맞는 장소를 찾아 투표를 만든다. 응답은 투표하기와 같은 모양이다.
     *
     * <p>AI 두 번과 장소 검색이 들어가서 응답이 몇 초에서 수십 초 걸릴 수 있다.
     * 투표에 올릴 장소를 못 찾으면 400이다.
     */
    @PostMapping
    public PollView create(@PathVariable("tripId") long tripId, @LoginUser Long userId,
                           @Valid @RequestBody PollCandidateCommand command) {
        return pollCandidateService.create(tripId, userId, command);
    }
}
