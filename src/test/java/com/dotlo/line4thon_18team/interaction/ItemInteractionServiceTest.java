package com.dotlo.line4thon_18team.interaction;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.interaction.dto.CommentResult;
import com.dotlo.line4thon_18team.interaction.entity.ItemLike;
import com.dotlo.line4thon_18team.interaction.port.InteractionAccess;
import com.dotlo.line4thon_18team.interaction.repository.ItemCommentRepository;
import com.dotlo.line4thon_18team.interaction.repository.ItemLikeRepository;
import com.dotlo.line4thon_18team.interaction.service.ItemInteractionService;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.*;

/** 실제 MySQL과 독립 트랜잭션. 접근 권한·최신 항목은 테스트 전용 대역이다. */
@SpringBootTest
@Import(ItemInteractionServiceTest.Config.class)
class ItemInteractionServiceTest {
    @Autowired ItemInteractionService service;
    @Autowired TestAccess access;
    @Autowired ItemLikeRepository likes;
    @Autowired ItemCommentRepository comments;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    Long owner, member, outsider, trip, otherTrip;
    static final String ITEM = "v-12345678";
    static final String OTHER_ITEM = "v-abcdefgh";

    @BeforeEach
    void prepare() {
        tx.executeWithoutResult(status -> {
            owner = newUser("소유자");
            member = newUser("동행자");
            outsider = newUser("외부인");
            trip = newTrip(owner);
            otherTrip = newTrip(owner);
        });
        access.members.put(trip, Set.of(owner, member));
        access.members.put(otherTrip, Set.of(owner));
        access.active.put(trip, Set.of(ITEM, OTHER_ITEM));
        access.active.put(otherTrip, Set.of(ITEM));
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(status -> {
            for (Long tripId : List.of(trip, otherTrip)) {
                em.createQuery("delete from ItemLike l where l.trip.id = :id").setParameter("id", tripId).executeUpdate();
                em.createQuery("delete from ItemComment c where c.trip.id = :id").setParameter("id", tripId).executeUpdate();
                em.remove(em.find(Trip.class, tripId));
            }
            em.flush();
            for (Long id : List.of(owner, member, outsider)) em.remove(em.find(User.class, id));
        });
        access.members.clear();
        access.active.clear();
    }

    @Test
    void 좋아요는_반복해도_사용자당_한_개이고_취소도_반복_가능하다() {
        assertThat(service.like(owner, trip, ITEM).count()).isEqualTo(1);
        assertThat(service.like(owner, trip, ITEM).count()).isEqualTo(1);
        assertThat(service.like(member, trip, ITEM).count()).isEqualTo(2);
        assertThat(service.unlike(owner, trip, ITEM).liked()).isFalse();
        assertThat(service.unlike(owner, trip, ITEM).count()).isEqualTo(1);
        assertThat(likes.existsByTripIdAndItemIdAndUserId(trip, ITEM, member)).isTrue();
        assertThat(service.like(owner, trip, ITEM).count()).isEqualTo(2);
    }

    @Test
    void 같은_항목ID라도_다른_여행의_좋아요와_댓글은_분리한다() {
        service.like(owner, trip, ITEM);
        assertThat(service.like(owner, otherTrip, ITEM).count()).isEqualTo(1);
        service.addComment(owner, trip, ITEM, "첫 여행");
        service.addComment(owner, otherTrip, ITEM, "둘째 여행");
        assertThat(service.listComments(owner, otherTrip)).extracting(CommentResult::content).containsExactly("둘째 여행");
    }

    @Test
    void 댓글은_여행의_항목을_합쳐_작성순으로_반환하고_작성자_정보를_포함한다() {
        var first = service.addComment(owner, trip, ITEM, " 이곳 좋아요\n같이 가요 ");
        var second = service.addComment(member, trip, OTHER_ITEM, "저도요");
        var result = service.listComments(member, trip);
        assertThat(result).extracting(CommentResult::id).containsExactly(first.id(), second.id());
        assertThat(result.get(0).content()).isEqualTo(" 이곳 좋아요\n같이 가요 ");
        assertThat(result.get(0).author().id()).isEqualTo(owner);
        assertThat(result.get(0).author().name()).isEqualTo("소유자");
        assertThat(result.get(0).createdAt()).isNotNull();
        assertThat(result.get(1).itemId()).isEqualTo(OTHER_ITEM);
    }

    @Test
    void 본인_댓글만_삭제할_수_있고_여행_소유자도_남의_댓글은_삭제할_수_없다() {
        var comment = service.addComment(member, trip, ITEM, "동행자의 의견");
        expect(ErrorCode.COMMENT_NOT_FOUND, () -> service.deleteComment(owner, comment.id()));
        assertThat(comments.existsById(comment.id())).isTrue();
        service.deleteComment(member, comment.id());
        assertThat(comments.existsById(comment.id())).isFalse();
        expect(ErrorCode.COMMENT_NOT_FOUND, () -> service.deleteComment(member, comment.id()));
    }

    @Test
    void 외부인은_좋아요_취소_댓글작성_조회가_모두_거절된다() {
        expect(ErrorCode.TRIP_NOT_FOUND, () -> service.like(outsider, trip, ITEM));
        expect(ErrorCode.TRIP_NOT_FOUND, () -> service.unlike(outsider, trip, ITEM));
        expect(ErrorCode.TRIP_NOT_FOUND, () -> service.addComment(outsider, trip, ITEM, "접근 불가"));
        expect(ErrorCode.TRIP_NOT_FOUND, () -> service.listComments(outsider, trip));
        assertThat(likes.countByTripIdAndItemId(trip, ITEM)).isZero();
        assertThat(service.listComments(owner, trip)).isEmpty();
    }

    @Test
    void 동행에서_빠진_작성자는_자신의_댓글도_삭제할_수_없다() {
        var comment = service.addComment(member, trip, ITEM, "남은 기록");
        access.members.put(trip, Set.of(owner));
        expect(ErrorCode.TRIP_NOT_FOUND, () -> service.deleteComment(member, comment.id()));
        assertThat(comments.existsById(comment.id())).isTrue();
    }

    @Test
    void 일정에서_빠진_항목은_숨기고_같은_ID로_돌아오면_기록도_다시_보인다() {
        service.like(owner, trip, ITEM);
        var comment = service.addComment(owner, trip, ITEM, "보존할 의견");
        service.addComment(member, trip, OTHER_ITEM, "현재 항목 의견");
        access.active.put(trip, Set.of(OTHER_ITEM));
        assertThat(service.listComments(owner, trip)).extracting(CommentResult::itemId).containsExactly(OTHER_ITEM);
        expect(ErrorCode.ITEM_NOT_FOUND, () -> service.like(owner, trip, ITEM));
        expect(ErrorCode.ITEM_NOT_FOUND, () -> service.unlike(owner, trip, ITEM));
        expect(ErrorCode.ITEM_NOT_FOUND, () -> service.addComment(owner, trip, ITEM, "추가 불가"));
        expect(ErrorCode.ITEM_NOT_FOUND, () -> service.deleteComment(owner, comment.id()));
        assertThat(comments.existsById(comment.id())).isTrue();
        assertThat(likes.countByTripIdAndItemId(trip, ITEM)).isEqualTo(1);
        access.active.put(trip, Set.of(ITEM, OTHER_ITEM));
        assertThat(service.listComments(owner, trip)).extracting(CommentResult::id).contains(comment.id());
        assertThat(service.like(owner, trip, ITEM).count()).isEqualTo(1);
    }

    @Test
    void 빈_일정은_빈_댓글목록이고_아무_항목에도_쓸_수_없다() {
        access.active.put(trip, Set.of());
        assertThat(service.listComments(owner, trip)).isEmpty();
        expect(ErrorCode.ITEM_NOT_FOUND, () -> service.like(owner, trip, ITEM));
    }

    @Test
    void 존재하지_않는_여행은_쓰기에서_거절된다() {
        expect(ErrorCode.TRIP_NOT_FOUND, () -> service.like(owner, Long.MAX_VALUE, ITEM));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t", "　"})
    void 빈_댓글은_저장하지_않는다(String text) {
        expect(ErrorCode.INVALID_INPUT, () -> service.addComment(owner, trip, ITEM, text));
        assertThat(service.listComments(owner, trip)).isEmpty();
    }

    @Test
    void 댓글은_TEXT_바이트_경계까지_저장하고_넘으면_입력오류다() {
        String boundary = "가".repeat(21_845);
        var saved = service.addComment(owner, trip, ITEM, boundary);
        assertThat(service.listComments(owner, trip).get(0).content()).isEqualTo(boundary);
        expect(ErrorCode.INVALID_INPUT, () -> service.addComment(owner, trip, ITEM, boundary + "a"));
        assertThat(comments.existsById(saved.id())).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void 빈_항목ID는_저장하지_않는다(String itemId) {
        expect(ErrorCode.INVALID_INPUT, () -> service.like(owner, trip, itemId));
    }

    @Test
    void 잘못된_숫자ID와_긴_항목ID는_입력오류다() {
        expect(ErrorCode.INVALID_INPUT, () -> service.like(null, trip, ITEM));
        expect(ErrorCode.INVALID_INPUT, () -> service.like(owner, 0L, ITEM));
        expect(ErrorCode.INVALID_INPUT, () -> service.listComments(owner, -1L));
        expect(ErrorCode.INVALID_INPUT, () -> service.deleteComment(owner, 0L));
        expect(ErrorCode.INVALID_INPUT, () -> service.like(owner, trip, "x".repeat(101)));
    }

    @Test
    void DB도_여행_항목_사용자_중복_좋아요를_막는다() {
        service.like(owner, trip, ITEM);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> likes.saveAndFlush(
                ItemLike.of(em.find(Trip.class, trip), ITEM, em.find(User.class, owner)))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(likes.countByTripIdAndItemId(trip, ITEM)).isEqualTo(1);
    }

    @Test
    void 항목ID는_대소문자를_구분한다() {
        access.active.put(trip, Set.of("v-abcdefgh", "v-ABCDEFGH"));
        service.like(owner, trip, "v-abcdefgh");
        service.like(owner, trip, "v-ABCDEFGH");
        service.addComment(owner, trip, "v-abcdefgh", "소문자");
        service.addComment(owner, trip, "v-ABCDEFGH", "대문자");
        access.active.put(trip, Set.of("v-ABCDEFGH"));
        assertThat(service.listComments(owner, trip)).extracting(CommentResult::content).containsExactly("대문자");
        assertThat(likes.countByTripIdAndItemId(trip, "v-abcdefgh")).isEqualTo(1);
        assertThat(likes.countByTripIdAndItemId(trip, "v-ABCDEFGH")).isEqualTo(1);
    }

    @Test
    void 동시_좋아요와_취소를_반복해도_중복이나_서버오류가_없다() throws Exception {
        for (int round = 0; round < 3; round++) {
            concurrent(12, i -> service.like(owner, trip, ITEM));
            assertThat(likes.countByTripIdAndItemId(trip, ITEM)).isEqualTo(1);
            concurrent(12, i -> service.unlike(owner, trip, ITEM));
            assertThat(likes.countByTripIdAndItemId(trip, ITEM)).isZero();
        }
    }

    @Test
    void 서로_다른_사용자의_동시_좋아요와_동시_댓글이_유실되지_않는다() throws Exception {
        concurrent(12, i -> service.like(i % 2 == 0 ? owner : member, trip, ITEM));
        assertThat(likes.countByTripIdAndItemId(trip, ITEM)).isEqualTo(2);
        concurrent(12, i -> service.addComment(i % 2 == 0 ? owner : member, trip, ITEM, "의견 " + i));
        assertThat(service.listComments(owner, trip)).hasSize(12)
                .extracting(CommentResult::content).doesNotHaveDuplicates();
    }

    @Test
    void 좋아요와_취소가_섞여도_마지막_명시적_요청대로_정리된다() throws Exception {
        concurrent(12, i -> {
            if (i % 2 == 0) service.like(owner, trip, ITEM);
            else service.unlike(owner, trip, ITEM);
        });
        assertThat(likes.countByTripIdAndItemId(trip, ITEM)).isBetween(0L, 1L);
        assertThat(service.like(owner, trip, ITEM).count()).isEqualTo(1);
        assertThat(service.unlike(owner, trip, ITEM).count()).isZero();
    }

    private Long newUser(String name) {
        var user = User.builder().googleSub(UUID.randomUUID().toString()).email("test@example.test").name(name).build();
        em.persist(user);
        return user.getId();
    }

    private Long newTrip(Long userId) {
        var value = Trip.create(em.find(User.class, userId), "오사카", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 3), Companion.ALONE, null);
        em.persist(value);
        return value.getId();
    }

    private static void expect(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }

    private static void concurrent(int count, IntConsumer action) throws Exception {
        var executor = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    action.accept(index);
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    static class TestAccess implements InteractionAccess {
        final ConcurrentMap<Long, Set<Long>> members = new ConcurrentHashMap<>();
        final ConcurrentMap<Long, Set<String>> active = new ConcurrentHashMap<>();

        @Override
        public Set<String> activeItemIds(Long userId, Long tripId) {
            if (!members.getOrDefault(tripId, Set.of()).contains(userId)) {
                throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
            }
            return active.getOrDefault(tripId, Set.of());
        }
    }

    @TestConfiguration
    static class Config {
        @Bean TestAccess interactionAccess() { return new TestAccess(); }
        @Bean ItemInteractionService itemInteractions(TestAccess access, ItemLikeRepository likes,
                ItemCommentRepository comments, EntityManager em, TransactionTemplate tx) {
            return new ItemInteractionService(access, likes, comments, em, tx);
        }
    }
}
