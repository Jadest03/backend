package com.dotlo.line4thon_18team.member;

import com.dotlo.line4thon_18team.member.entity.TripInvite;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripInviteRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 동행: 초대 링크, 미리보기, 수락, 구성원 목록. MySQL이 필요하다 (docker compose up -d).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class InviteControllerTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository tripMembers;
    @Autowired TripInviteRepository tripInvites;

    private User owner;
    private User friend;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser("owner", "방장"));
        User newFriend = newUser("friend", "친구");
        newFriend.updateMyProfile("친구", null, 4);
        friend = users.save(newFriend);
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.FRIEND, 3));
    }

    @Test
    void 초대_링크를_다시_요청하면_만료_전_링크를_그대로_준다() throws Exception {
        String first = createInvite(owner);

        perform(post("/api/trips/{tripId}/invites", trip.getId()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(first));
    }

    @Test
    void 혼자_가는_여행도_초대할_수_있다() throws Exception {
        Trip solo = trips.save(Trip.create(owner, "도쿄", START, START.plusDays(1), Companion.ALONE, null));

        perform(post("/api/trips/{tripId}/invites", solo.getId()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void 초대_미리보기는_로그인_없이_볼_수_있다() throws Exception {
        String token = createInvite(owner);

        mvc.perform(get("/api/invites/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("오사카"))
                .andExpect(jsonPath("$.inviterName").value("방장"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.maxMembers").value(10));
    }

    @Test
    void 수락하면_구성원이_되어_여행과_구성원_목록을_볼_수_있다() throws Exception {
        String token = createInvite(owner);
        perform(get("/api/trips/{tripId}", trip.getId()), friend).andExpect(status().isNotFound());

        perform(post("/api/invites/{token}/accept", token), friend)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId()));

        perform(get("/api/trips/{tripId}", trip.getId()), friend).andExpect(status().isOk());
        perform(get("/api/trips"), friend).andExpect(jsonPath("$[0].id").value(trip.getId()));
        perform(get("/api/trips/{tripId}/members", trip.getId()), friend)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].me").value(false))
                .andExpect(jsonPath("$[1].name").value("친구"))
                .andExpect(jsonPath("$[1].characterId").value(4))
                .andExpect(jsonPath("$[1].me").value(true));
    }

    @Test
    void 이미_구성원이면_다시_수락해도_그대로_통과한다() throws Exception {
        String token = createInvite(owner);
        perform(post("/api/invites/{token}/accept", token), friend).andExpect(status().isOk());

        perform(post("/api/invites/{token}/accept", token), friend).andExpect(status().isOk());
        perform(post("/api/invites/{token}/accept", token), owner).andExpect(status().isOk());

        perform(get("/api/trips/{tripId}/members", trip.getId()), owner)
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 만료된_링크는_미리보기와_수락이_안_된다() throws Exception {
        tripInvites.save(TripInvite.create(trip, "expired-token", owner.getId(), LocalDateTime.now().minusMinutes(1)));

        mvc.perform(get("/api/invites/{token}", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_EXPIRED"));
        perform(post("/api/invites/{token}/accept", "expired-token"), friend)
                .andExpect(jsonPath("$.code").value("INVITE_EXPIRED"));
    }

    @Test
    void 인원이_가득_차면_수락할_수_없다() throws Exception {
        String token = createInvite(owner);
        for (int i = 0; i < 9; i++) {
            tripMembers.save(TripMember.create(trip, users.save(newUser("member" + i, "구성원" + i))));
        }

        perform(post("/api/invites/{token}/accept", token), friend)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_MEMBER_FULL"));
    }

    @Test
    void 없는_링크는_404() throws Exception {
        mvc.perform(get("/api/invites/{token}", "no-such-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
    }

    @Test
    void 구성원이_아니면_초대와_구성원_목록이_안_된다() throws Exception {
        perform(post("/api/trips/{tripId}/invites", trip.getId()), friend)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        perform(get("/api/trips/{tripId}/members", trip.getId()), friend)
                .andExpect(status().isNotFound());
    }

    private String createInvite(User user) throws Exception {
        String body = perform(post("/api/trips/{tripId}/invites", trip.getId()), user)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("token").asString();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User user) throws Exception {
        return mvc.perform(request.header("X-User-Id", user.getId()));
    }

    private static User newUser(String sub, String name) {
        return User.builder().googleSub("sub-invite-" + sub).email(sub + "@test.com").name(name).build();
    }
}
