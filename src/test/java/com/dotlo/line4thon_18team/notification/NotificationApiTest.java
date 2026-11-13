package com.dotlo.line4thon_18team.notification;

import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.notification.entity.NotificationType;
import com.dotlo.line4thon_18team.notification.repository.NotificationRepository;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 홈 알림 목록·읽음, 일행에게 보내기, 나가기·여행 삭제 때 정리. MySQL이 필요하다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class NotificationApiTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository members;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationService notificationService;

    private User owner;
    private User first;
    private User second;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser());
        first = users.save(newUser());
        second = users.save(newUser());
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.FRIEND, 3));
        members.save(TripMember.create(trip, first));
        members.save(TripMember.create(trip, second));
    }

    @Test
    void 일행에게_보내면_행동한_사람만_빼고_받는다() throws Exception {
        notificationService.notifyTripMembers(trip.getId(), first.getId(), NotificationType.PLACE_ADDED,
                "민지님이 2일차에 도요스 수변 공원 외 1곳을 추가했어요", "도요스 수변 공원");

        list(owner.getId())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("PLACE_ADDED"))
                .andExpect(jsonPath("$.notifications[0].tripId").value(trip.getId()))
                .andExpect(jsonPath("$.notifications[0].tripTitle").value("오사카 2박 3일"))
                .andExpect(jsonPath("$.notifications[0].highlight").value("도요스 수변 공원"))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
        list(second.getId()).andExpect(jsonPath("$.notifications.length()").value(1));
        list(first.getId()).andExpect(jsonPath("$.notifications.length()").value(0));
    }

    @Test
    void 사람이_없는_알림은_모두_받는다() throws Exception {
        notificationService.notifyTripMembers(trip.getId(), null, NotificationType.AI_DRAFT_READY,
                "AI 초안이 완성됐어요. 1–3일차 일정을 확인해 보세요", null);
        for (User user : new User[]{owner, first, second}) {
            list(user.getId()).andExpect(jsonPath("$.notifications.length()").value(1));
        }
    }

    @Test
    void 하나_읽기와_모두_읽기() throws Exception {
        notificationService.notifyUser(trip.getId(), owner, NotificationType.COMMENT_MENTION, "멘션 1", null);
        notificationService.notifyUser(trip.getId(), owner, NotificationType.COMMENT_MENTION, "멘션 2", null);
        unread(owner.getId(), true);

        Long firstId = notifications.findTop50ByUserIdOrderByIdDesc(owner.getId()).get(1).getId();
        perform(patch("/api/notifications/{id}/read", firstId), owner.getId()).andExpect(status().isNoContent());
        list(owner.getId())
                .andExpect(jsonPath("$.notifications[0].message").value("멘션 2"))
                .andExpect(jsonPath("$.notifications[0].read").value(false))
                .andExpect(jsonPath("$.notifications[1].read").value(true));
        unread(owner.getId(), true);

        perform(patch("/api/notifications/read-all"), owner.getId()).andExpect(status().isNoContent());
        unread(owner.getId(), false);
    }

    @Test
    void 남의_알림은_읽음_처리할_수_없다() throws Exception {
        notificationService.notifyUser(trip.getId(), owner, NotificationType.COMMENT_MENTION, "멘션", null);
        Long id = notifications.findTop50ByUserIdOrderByIdDesc(owner.getId()).get(0).getId();
        perform(patch("/api/notifications/{id}/read", id), first.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void 목록은_최근_50개_최신순() throws Exception {
        for (int i = 1; i <= 55; i++) {
            notificationService.notifyUser(trip.getId(), owner, NotificationType.COMMENT_MENTION, "n" + i, null);
        }
        list(owner.getId())
                .andExpect(jsonPath("$.notifications.length()").value(50))
                .andExpect(jsonPath("$.notifications[0].message").value("n55"))
                .andExpect(jsonPath("$.notifications[49].message").value("n6"));
    }

    @Test
    void 여행에서_나가면_그_여행_알림만_지워진다() throws Exception {
        notificationService.notifyTripMembers(trip.getId(), null, NotificationType.AI_DRAFT_READY, "초안", null);
        perform(delete("/api/trips/{tripId}/members/me", trip.getId()), first.getId()).andExpect(status().isOk());

        list(first.getId()).andExpect(jsonPath("$.notifications.length()").value(0));
        list(second.getId()).andExpect(jsonPath("$.notifications.length()").value(1));
    }

    @Test
    void 여행을_지우면_알림도_지워진다() throws Exception {
        notificationService.notifyTripMembers(trip.getId(), null, NotificationType.AI_DRAFT_READY, "초안", null);
        perform(delete("/api/trips/{tripId}", trip.getId()), owner.getId()).andExpect(status().isNoContent());
        assertThat(notifications.findTop50ByUserIdOrderByIdDesc(second.getId())).isEmpty();
    }

    private ResultActions list(Long userId) throws Exception {
        return perform(get("/api/notifications"), userId).andExpect(status().isOk());
    }

    private void unread(Long userId, boolean expected) throws Exception {
        perform(get("/api/notifications/unread"), userId).andExpect(jsonPath("$.hasUnread").value(expected));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Long userId) throws Exception {
        return mvc.perform(request.header("X-User-Id", userId));
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-noti-" + id).email(id + "@test.com").name("n" + id).build();
    }
}
