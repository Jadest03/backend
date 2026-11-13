package com.dotlo.line4thon_18team.interaction;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.interaction.service.ItemInteractionService;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.trip.service.TripDeleteService;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 삭제와 좋아요·삭제가 동시에 들어와도 500 이 아니라 COMMENT_NOT_FOUND 가 되는지.
 * 요청마다 트랜잭션이 따로 열려야 해서 테스트 트랜잭션 없이 돌리고, 끝에 여행째 지운다. MySQL이 필요하다.
 */
@SpringBootTest
class CommentRaceTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);
    private static final String ITEM = "v-race0001";
    private static final int ROUNDS = 15;

    @Autowired ItemInteractionService service;
    @Autowired ItineraryService itineraryService;
    @Autowired TripDeleteService tripDeleteService;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository members;

    private final List<User> created = new ArrayList<>();
    private User owner;
    private User member;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = newUser();
        member = newUser();
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.FRIEND, 2));
        members.save(TripMember.create(trip, member));
        itineraryService.save(trip.getId(), owner.getId(), ItinerarySource.AI_GENERATED, null,
                ItineraryDraft.of(DayDraft.of(START, VisitDraft.of(ITEM, "ChIJ-race"))),
                ValidationResult.of(List.of(), List.of()));
    }

    @AfterEach
    void tearDown() {
        tripDeleteService.deleteTrip(owner.getId(), trip.getId());
        users.deleteAll(created);
    }

    @Test
    void 댓글을_지우는_사이_좋아요가_들어와도_500이_아니다() throws Exception {
        for (int i = 0; i < ROUNDS; i++) {
            long id = service.addComment(owner.getId(), trip.getId(), ITEM, "댓글", null).id();
            race(() -> service.likeComment(member.getId(), id), () -> {
                service.deleteComment(owner.getId(), id);
                return null;
            });
        }
    }

    @Test
    void 같은_댓글을_동시에_두_번_지워도_한_번만_지워진다() throws Exception {
        for (int i = 0; i < ROUNDS; i++) {
            long id = service.addComment(owner.getId(), trip.getId(), ITEM, "댓글", null).id();
            Callable<Object> delete = () -> {
                service.deleteComment(owner.getId(), id);
                return null;
            };
            assertThat(race(delete, delete)).isEqualTo(1);
        }
    }

    // 두 요청을 동시에 출발시킨다. 실패는 COMMENT_NOT_FOUND 만 허용하고, 그 개수를 돌려준다
    private int race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> results = new ArrayList<>();
        for (Callable<?> task : List.of(first, second)) {
            results.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        int notFound = 0;
        for (Future<?> result : results) {
            try {
                result.get();
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
                notFound++;
            }
        }
        pool.shutdown();
        return notFound;
    }

    private User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(User.builder().googleSub("sub-race-" + id).email(id + "@test.com").name("테스트").build());
        created.add(user);
        return user;
    }
}
