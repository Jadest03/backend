package com.dotlo.line4thon_18team.chat;

import com.dotlo.line4thon_18team.chat.entity.ChatMessage;
import com.dotlo.line4thon_18team.chat.repository.ChatMessageRepository;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 여행 채팅방 메시지 보내기·가져오기(폴링), 서버가 넣는 SYSTEM·AI 메시지. MySQL이 필요하다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class ChatMessageApiTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);
    private static final String URL = "/api/trips/{tripId}/chat/messages";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository members;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatMessageService chatMessageService;

    private User owner;
    private User member;
    private Long strangerId;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser(3));
        member = users.save(newUser(7));
        strangerId = users.save(newUser(1)).getId();
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.FRIEND, 2));
        members.save(TripMember.create(trip, member));
    }

    @Test
    void 구성원이_보낸_메시지를_순서대로_가져오고_after_로_새_것만_받는다() throws Exception {
        send(owner.getId(), "오사카성 갈까요?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("USER"))
                .andExpect(jsonPath("$.sender.nickname").value(owner.getNickname()))
                .andExpect(jsonPath("$.sender.characterId").value(3));
        send(member.getId(), "좋아요!").andExpect(status().isOk());

        perform(get(URL, trip.getId()), member.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].text").value("오사카성 갈까요?"))
                .andExpect(jsonPath("$.messages[1].sender.id").value(member.getId()))
                .andExpect(jsonPath("$.messages[1].payload").value(nullValue()));

        Long firstId = allMessages().get(0).getId();
        perform(get(URL, trip.getId()).param("after", firstId.toString()), owner.getId())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].text").value("좋아요!"));
    }

    @Test
    void 처음에는_처음부터_한_번에_100개씩_가져온다() throws Exception {
        for (int i = 1; i <= 105; i++) {
            chatMessageService.appendSystem(trip.getId(), "m" + i);
        }
        perform(get(URL, trip.getId()), owner.getId())
                .andExpect(jsonPath("$.messages.length()").value(100))
                .andExpect(jsonPath("$.messages[0].text").value("m1"))
                .andExpect(jsonPath("$.messages[99].text").value("m100"));

        Long hundredthId = allMessages().get(99).getId();
        perform(get(URL, trip.getId()).param("after", hundredthId.toString()), owner.getId())
                .andExpect(jsonPath("$.messages.length()").value(5))
                .andExpect(jsonPath("$.messages[0].text").value("m101"));
    }

    @Test
    void 시스템_알림과_도틀로_응답은_보낸_사람_없이_payload_를_받은_그대로_내려준다() throws Exception {
        chatMessageService.appendSystem(trip.getId(), "정다은님이 여행에 참여했어요");
        chatMessageService.appendAi(trip.getId(), "부담 없는 곳으로 골랐어요.", "{\"card\":{\"kind\":\"POLL\",\"pollId\":7}}");

        perform(get(URL, trip.getId()), owner.getId())
                .andExpect(jsonPath("$.messages[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.messages[0].sender").value(nullValue()))
                .andExpect(jsonPath("$.messages[0].payload").value(nullValue()))
                .andExpect(jsonPath("$.messages[1].type").value("AI"))
                .andExpect(jsonPath("$.messages[1].sender").value(nullValue()))
                .andExpect(jsonPath("$.messages[1].payload.card.kind").value("POLL"))
                .andExpect(jsonPath("$.messages[1].payload.card.pollId").value(7));
    }

    @Test
    void 전송_API_는_type_을_보내도_사람_메시지로_저장한다() throws Exception {
        perform(post(URL, trip.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"도틀로인 척\",\"type\":\"AI\"}"), member.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("USER"))
                .andExpect(jsonPath("$.sender.id").value(member.getId()));
    }

    @Test
    void 빈_글이나_500자를_넘는_글은_400() throws Exception {
        send(owner.getId(), " ").andExpect(status().isBadRequest());
        send(owner.getId(), "가".repeat(501)).andExpect(status().isBadRequest());
        assertThat(allMessages()).isEmpty();
    }

    @Test
    void 구성원이_아니면_404() throws Exception {
        send(strangerId, "안녕").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        perform(get(URL, trip.getId()), strangerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void 여행을_지우면_메시지도_지워진다() throws Exception {
        send(owner.getId(), "안녕").andExpect(status().isOk());
        perform(delete("/api/trips/{tripId}", trip.getId()), owner.getId()).andExpect(status().isNoContent());
        assertThat(allMessages()).isEmpty();
    }

    private List<ChatMessage> allMessages() {
        return messages.findTop100ByTripIdAndIdGreaterThanOrderByIdAsc(trip.getId(), 0L);
    }

    private ResultActions send(Long userId, String text) throws Exception {
        return perform(post(URL, trip.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}"), userId);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Long userId) throws Exception {
        return mvc.perform(request.header("X-User-Id", userId));
    }

    private static User newUser(int characterId) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder().googleSub("sub-chat-" + id).email(id + "@test.com").name("구글이름").build();
        user.completeSignup("c" + id, characterId, false, LocalDateTime.now());
        return user;
    }
}
