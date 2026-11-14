package com.dotlo.line4thon_18team.chat.poll;

import com.dotlo.line4thon_18team.chat.poll.dto.NewCandidate;
import com.dotlo.line4thon_18team.chat.poll.dto.PollView;
import com.dotlo.line4thon_18team.chat.poll.repository.TripPollRepository;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.ItineraryTripCleanupService;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.member.service.TripLeaveService;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 채팅방 투표 API (COLLAB-POLL-001). MySQL 이 필요하다 (docker compose up -d).
 * 투표를 만드는 API 는 없어서 설정은 PollService.create 로 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class ChatPollApiTest {

    @Autowired MockMvc mvc;
    @Autowired PollService pollService;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository tripMembers;
    @Autowired TripLeaveService tripLeaveService;
    @Autowired TripPollRepository polls;
    @Autowired ItineraryTripCleanupService cleanupService;

    private Trip trip;
    private Long owner;
    private Long companion;

    @BeforeEach
    void setUp() {
        owner = newUser("방장").getId();
        User companionUser = newUser("동행자");
        companion = companionUser.getId();
        trip = trips.save(Trip.create(users.findById(owner).orElseThrow(), "오사카",
                LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 17), Companion.FRIEND, null));
        tripMembers.save(TripMember.create(trip, companionUser));   // 구성원 2명
    }

    @Test
    void 투표하면_표가_올라간다() throws Exception {
        PollView poll = givenPoll();

        vote(owner, poll.pollId(), poll.candidates().get(0).id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.totalMembers").value(2))
                .andExpect(jsonPath("$.votedCount").value(1))
                .andExpect(jsonPath("$.candidates[0].votes").value(1))
                .andExpect(jsonPath("$.candidates[0].voterUserIds[0]").value(owner))
                .andExpect(jsonPath("$.candidates[1].votes").value(0));
    }

    @Test
    void 다시_투표하면_표가_옮겨간다() throws Exception {
        PollView poll = givenPoll();
        vote(owner, poll.pollId(), poll.candidates().get(0).id()).andExpect(status().isOk());

        vote(owner, poll.pollId(), poll.candidates().get(1).id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votedCount").value(1))   // 사람 수는 그대로
                .andExpect(jsonPath("$.candidates[0].votes").value(0))
                .andExpect(jsonPath("$.candidates[1].votes").value(1));
    }

    @Test
    void 구성원이_모두_투표하면_마감된다() throws Exception {
        PollView poll = givenPoll();
        vote(owner, poll.pollId(), poll.candidates().get(0).id())
                .andExpect(jsonPath("$.status").value("OPEN"));

        vote(companion, poll.pollId(), poll.candidates().get(0).id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.votedCount").value(2));
    }

    @Test
    void 끝난_투표에는_더_투표할_수_없다() throws Exception {
        PollView poll = givenPoll();
        vote(owner, poll.pollId(), poll.candidates().get(0).id());
        vote(companion, poll.pollId(), poll.candidates().get(0).id());

        vote(owner, poll.pollId(), poll.candidates().get(1).id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POLL_CLOSED"));
    }

    @Test
    void 그_투표의_후보가_아니면_400이다() throws Exception {
        PollView poll = givenPoll();
        PollView other = givenPoll();

        vote(owner, poll.pollId(), other.candidates().get(0).id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POLL_CANDIDATE"));
    }

    @Test
    void 없는_투표는_404다() throws Exception {
        vote(owner, 999_999L, 1L).andExpect(status().isNotFound());
    }

    @Test
    void 구성원이_아니면_404다() throws Exception {
        PollView poll = givenPoll();
        Long stranger = newUser("남").getId();

        vote(stranger, poll.pollId(), poll.candidates().get(0).id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void 최근_투표를_새것부터_돌려준다() throws Exception {
        PollView first = givenPoll();
        PollView second = givenPoll();

        mvc.perform(get("/api/trips/{tripId}/chat/polls", trip.getId()).header("X-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pollId").value(second.pollId()))
                .andExpect(jsonPath("$[1].pollId").value(first.pollId()))
                .andExpect(jsonPath("$[0].totalMembers").value(2));
    }

    @Test
    void 나간_사람의_표는_집계와_마감에서_제외한다() throws Exception {
        User remaining = newUser("남은 동행자");
        tripMembers.save(TripMember.create(trip, remaining));
        PollView poll = givenPoll();
        long firstCandidate = poll.candidates().get(0).id();
        long secondCandidate = poll.candidates().get(1).id();
        vote(companion, poll.pollId(), firstCandidate).andExpect(status().isOk());
        tripLeaveService.leave(trip.getId(), companion);

        vote(owner, poll.pollId(), secondCandidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.votedCount").value(1))
                .andExpect(jsonPath("$.totalMembers").value(2))
                .andExpect(jsonPath("$.candidates[0].voterUserIds").isEmpty());

        mvc.perform(get("/api/trips/{tripId}/chat/polls", trip.getId()).header("X-User-Id", remaining.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].votedCount").value(1))
                .andExpect(jsonPath("$[0].candidates[0].votes").value(0));

        vote(remaining.getId(), poll.pollId(), secondCandidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.votedCount").value(2));
    }

    @Test
    void 후보가_하나뿐이면_투표를_만들지_않는다() {
        assertThatThrownBy(() -> pollService.create(trip.getId(), owner, "어디 갈까요?",
                List.of(new NewCandidate("p-1", "오사카성 공원", "공원"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_POLL);
    }

    @Test
    void 여행을_지우면_투표도_같이_지워진다() throws Exception {
        PollView poll = givenPoll();
        vote(owner, poll.pollId(), poll.candidates().get(0).id());

        cleanupService.deleteByTrip(trip.getId());

        assertThat(polls.findById(poll.pollId())).isEmpty();
    }

    // ---- helpers ----

    private PollView givenPoll() {
        return pollService.create(trip.getId(), owner, "2일차에 어디 갈까요?", List.of(
                new NewCandidate("p-castle", "오사카성 공원", "공원"),
                new NewCandidate("p-aquarium", "가이유칸 수족관", "수족관"),
                new NewCandidate("p-wheel", "덴포잔 대관람차", "전망")));
    }

    private ResultActions vote(Long userId, long pollId, long candidateId) throws Exception {
        return mvc.perform(post("/api/trips/{tripId}/chat/polls/{pollId}/votes", trip.getId(), pollId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateId\": " + candidateId + "}"));
    }

    private User newUser(String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.builder().googleSub("sub-poll-" + id).email(id + "@test.com").name(name).build());
    }
}
