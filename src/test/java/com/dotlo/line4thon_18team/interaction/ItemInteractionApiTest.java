package com.dotlo.line4thon_18team.interaction;

import com.dotlo.line4thon_18team.interaction.repository.CommentLikeRepository;
import com.dotlo.line4thon_18team.interaction.repository.ItemCommentRepository;
import com.dotlo.line4thon_18team.interaction.repository.ItemLikeRepository;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.notification.entity.Notification;
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

/** 일정 항목 좋아요·댓글·답글·댓글 좋아요 API. 실제 일정 저장과 구성원 확인으로 검증한다. MySQL이 필요하다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class ItemInteractionApiTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);
    private static final String A = "v-aaaa0001";
    private static final String B = "v-bbbb0002";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository members;
    @Autowired ItineraryService itineraryService;
    @Autowired ItemLikeRepository itemLikes;
    @Autowired ItemCommentRepository comments;
    @Autowired CommentLikeRepository commentLikes;
    @Autowired NotificationRepository notifications;

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
        saveItinerary(A, B);
    }

    @Test
    void 장소_좋아요는_한_사람에_한_번이고_취소된다() throws Exception {
        perform(post(itemUrl(A, "likes")), member.getId()).andExpect(jsonPath("$.count").value(1));
        perform(post(itemUrl(A, "likes")), member.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.liked").value(true));
        perform(post(itemUrl(A, "likes")), owner.getId()).andExpect(jsonPath("$.count").value(2));

        perform(delete(itemUrl(A, "likes")), member.getId())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.liked").value(false));

        list(member.getId())
                .andExpect(jsonPath("$.items[0].itemId").value(A))
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.items[1].itemId").value(B))
                .andExpect(jsonPath("$.items[1].likeCount").value(0));
    }

    @Test
    void 답글은_한_단계로_붙고_댓글_수에_포함된다() throws Exception {
        long rootId = comment(owner.getId(), A, "여기 꼭 가요!", null);
        long replyId = comment(member.getId(), A, "@" + owner.getNickname() + " 좋아요", rootId);
        comment(owner.getId(), A, "@" + member.getNickname() + " 아침에 가요", replyId);

        list(member.getId())
                .andExpect(jsonPath("$.items[0].commentCount").value(3))
                .andExpect(jsonPath("$.items[1].commentCount").value(0))
                .andExpect(jsonPath("$.comments.length()").value(3))
                .andExpect(jsonPath("$.comments[0].parentId").value(nullValue()))
                .andExpect(jsonPath("$.comments[0].author.nickname").value(owner.getNickname()))
                .andExpect(jsonPath("$.comments[0].author.characterId").value(3))
                .andExpect(jsonPath("$.comments[1].parentId").value(rootId))
                .andExpect(jsonPath("$.comments[2].parentId").value(rootId))
                .andExpect(jsonPath("$.comments[2].content").value("@" + member.getNickname() + " 아침에 가요"));
    }

    @Test
    void 다른_장소의_댓글에는_답글을_달_수_없다() throws Exception {
        long rootId = comment(owner.getId(), A, "여기 꼭 가요!", null);
        perform(post(itemUrl(B, "comments")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"답글\",\"parentId\":" + rootId + "}"), member.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void 댓글_좋아요는_한_사람에_한_번이고_내가_눌렀는지_보인다() throws Exception {
        long id = comment(owner.getId(), A, "여기 꼭 가요!", null);
        perform(post("/api/comments/{id}/likes", id), member.getId()).andExpect(jsonPath("$.count").value(1));
        perform(post("/api/comments/{id}/likes", id), member.getId()).andExpect(jsonPath("$.count").value(1));

        list(member.getId())
                .andExpect(jsonPath("$.comments[0].likeCount").value(1))
                .andExpect(jsonPath("$.comments[0].liked").value(true));
        list(owner.getId()).andExpect(jsonPath("$.comments[0].liked").value(false));

        perform(delete("/api/comments/{id}/likes", id), member.getId())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    void 쓴_사람만_지우고_댓글을_지우면_답글과_좋아요도_지워진다() throws Exception {
        long rootId = comment(owner.getId(), A, "여기 꼭 가요!", null);
        long replyId = comment(member.getId(), A, "좋아요", rootId);
        perform(post("/api/comments/{id}/likes", replyId), owner.getId()).andExpect(status().isOk());

        perform(delete("/api/comments/{id}", rootId), member.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));

        perform(delete("/api/comments/{id}", rootId), owner.getId()).andExpect(status().isNoContent());
        list(owner.getId())
                .andExpect(jsonPath("$.comments.length()").value(0))
                .andExpect(jsonPath("$.items[0].commentCount").value(0));
        assertThat(commentLikes.findAllByCommentTripId(trip.getId())).isEmpty();
    }

    @Test
    void 일정에서_빠진_장소는_목록에서_숨기고_새로_쓸_수_없다() throws Exception {
        comment(owner.getId(), B, "여기도요", null);
        perform(post(itemUrl(B, "likes")), member.getId()).andExpect(status().isOk());
        saveItinerary(A);

        perform(delete(itemUrl(B, "likes")), member.getId())   // 취소는 된다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        list(owner.getId())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.comments.length()").value(0));
        perform(post(itemUrl(B, "likes")), owner.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
        perform(post(itemUrl("v-없는항목", "likes")), owner.getId())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));

        saveItinerary(A, B);   // 다시 넣으면 기록이 돌아온다
        list(owner.getId()).andExpect(jsonPath("$.comments[0].content").value("여기도요"));
    }

    @Test
    void 빈_글이나_500자를_넘는_댓글은_400() throws Exception {
        for (String content : List.of(" ", "가".repeat(501))) {
            perform(post(itemUrl(A, "comments")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"" + content + "\"}"), owner.getId())
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void 구성원이_아니면_여행은_404_댓글은_COMMENT_NOT_FOUND() throws Exception {
        long id = comment(owner.getId(), A, "여기 꼭 가요!", null);
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/trips/{tripId}/comments", trip.getId()), post(itemUrl(A, "likes")))) {
            perform(request, strangerId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        }
        for (MockHttpServletRequestBuilder request : List.of(
                post("/api/comments/{id}/likes", id), delete("/api/comments/{id}", id))) {
            perform(request, strangerId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        }
    }

    @Test
    void 여행을_지우면_좋아요와_댓글도_지워진다() throws Exception {
        long id = comment(owner.getId(), A, "여기 꼭 가요!", null);
        perform(post(itemUrl(A, "likes")), owner.getId()).andExpect(status().isOk());
        perform(post("/api/comments/{id}/likes", id), member.getId()).andExpect(status().isOk());

        perform(delete("/api/trips/{tripId}", trip.getId()), owner.getId()).andExpect(status().isNoContent());

        assertThat(itemLikes.findAllByTripId(trip.getId())).isEmpty();
        assertThat(comments.findAllByParentId(id)).isEmpty();
        assertThat(comments.findById(id)).isEmpty();
        assertThat(commentLikes.findAllByCommentTripId(trip.getId())).isEmpty();
    }

    @Test
    void 댓글에서_부른_일행에게만_멘션_알림이_간다() throws Exception {
        User outsider = users.save(newUser(5));
        comment(member.getId(), A, "@" + owner.getNickname() + " @" + outsider.getNickname() + " @"
                + member.getNickname() + " 여기 가요", null);

        List<Notification> received = notifications.findTop50ByUserIdOrderByIdDesc(owner.getId());
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getType()).isEqualTo(NotificationType.COMMENT_MENTION);
        assertThat(received.get(0).getMessage()).startsWith(member.getNickname() + "님이 나를 멘션했어요 \"@");
        assertThat(notifications.findTop50ByUserIdOrderByIdDesc(outsider.getId())).isEmpty();   // 일행 아님
        assertThat(notifications.findTop50ByUserIdOrderByIdDesc(member.getId())).isEmpty();     // 나 자신
    }

    @Test
    void 긴_닉네임을_짧은_닉네임으로_잘못_잡지_않는다() throws Exception {
        User shortName = users.save(namedUser("s" + UUID.randomUUID().toString().substring(0, 6)));
        User longName = users.save(namedUser(shortName.getNickname() + "z"));
        members.save(TripMember.create(trip, shortName));
        members.save(TripMember.create(trip, longName));

        comment(owner.getId(), A, "@" + longName.getNickname() + " 같이 가요", null);

        assertThat(notifications.findTop50ByUserIdOrderByIdDesc(longName.getId())).hasSize(1);
        assertThat(notifications.findTop50ByUserIdOrderByIdDesc(shortName.getId())).isEmpty();
    }

    private void saveItinerary(String... itemIds) {
        VisitDraft[] visits = new VisitDraft[itemIds.length];
        for (int i = 0; i < itemIds.length; i++) {
            visits[i] = VisitDraft.of(itemIds[i], "ChIJ-" + i);
        }
        itineraryService.save(trip.getId(), owner.getId(), ItinerarySource.AI_GENERATED, null,
                ItineraryDraft.of(DayDraft.of(START, visits)), ValidationResult.of(List.of(), List.of()));
    }

    private long comment(Long userId, String itemId, String content, Long parentId) throws Exception {
        String json = perform(post(itemUrl(itemId, "comments")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\",\"parentId\":" + parentId + "}"), userId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions list(Long userId) throws Exception {
        return perform(get("/api/trips/{tripId}/comments", trip.getId()), userId).andExpect(status().isOk());
    }

    private String itemUrl(String itemId, String what) {
        return "/api/trips/" + trip.getId() + "/items/" + itemId + "/" + what;
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Long userId) throws Exception {
        return mvc.perform(request.header("X-User-Id", userId));
    }

    private static User namedUser(String nickname) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder().googleSub("sub-ia-" + id).email(id + "@test.com").name("구글이름").build();
        user.completeSignup(nickname, 1, false, LocalDateTime.now());
        return user;
    }

    private static User newUser(int characterId) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder().googleSub("sub-ia-" + id).email(id + "@test.com").name("구글이름").build();
        user.completeSignup("i" + id, characterId, false, LocalDateTime.now());
        return user;
    }
}
