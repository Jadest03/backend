package com.dotlo.line4thon_18team.chat.poll;

import com.dotlo.line4thon_18team.chat.poll.dto.NewCandidate;
import com.dotlo.line4thon_18team.chat.poll.dto.PollView;
import com.dotlo.line4thon_18team.chat.poll.entity.PollStatus;
import com.dotlo.line4thon_18team.chat.poll.repository.TripPollRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 서로 다른 트랜잭션에서 실행되는 마지막 두 표와 여행 삭제를 검증한다. */
@SpringBootTest
class PollConcurrencyTest {

    @Autowired PollService pollService;
    @Autowired TripDeleteService tripDeleteService;
    @Autowired TransactionTemplate transactions;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository members;
    @Autowired TripPollRepository polls;

    private final List<User> createdUsers = new ArrayList<>();
    private User owner;
    private User firstMember;
    private User secondMember;
    private Trip trip;
    private PollView poll;

    @BeforeEach
    void setUp() {
        owner = newUser();
        firstMember = newUser();
        secondMember = newUser();
        LocalDate start = LocalDate.of(2026, 12, 14);
        trip = trips.save(Trip.create(owner, "오사카", start, start.plusDays(3), Companion.FRIEND, 3));
        members.save(TripMember.create(trip, firstMember));
        members.save(TripMember.create(trip, secondMember));
        poll = pollService.create(trip.getId(), owner.getId(), "어디 갈까요?", List.of(
                new NewCandidate("p-park", "공원", "산책"),
                new NewCandidate("p-museum", "박물관", "전시")));
    }

    @AfterEach
    void tearDown() {
        if (trip != null && trips.existsById(trip.getId())) {
            tripDeleteService.deleteTrip(owner.getId(), trip.getId());
        }
        users.deleteAll(createdUsers);
    }

    @Test
    void 마지막_두_명이_겹쳐_투표하면_마지막_투표에서_마감된다() throws Exception {
        vote(owner);

        PollView result = duringUncommittedVote(() -> vote(secondMember));

        assertThat(result.status()).isEqualTo(PollStatus.CLOSED);
        assertThat(result.votedCount()).isEqualTo(3);
        assertThat(polls.findById(poll.pollId()).orElseThrow().getStatus()).isEqualTo(PollStatus.CLOSED);
    }

    @Test
    void 투표를_저장하는_중에_여행을_삭제하면_투표도_정리된다() throws Exception {
        duringUncommittedVote(() -> {
            tripDeleteService.deleteTrip(owner.getId(), trip.getId());
            return null;
        });

        assertThat(trips.existsById(trip.getId())).isFalse();
        assertThat(polls.existsById(poll.pollId())).isFalse();
    }

    private <T> T duringUncommittedVote(Supplier<T> next) throws Exception {
        CountDownLatch voted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> voting = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            vote(firstMember);
            voted.countDown();
            await(commit);
        }));

        CompletableFuture<T> other;
        try {
            await(voted);
            CountDownLatch started = new CountDownLatch(1);
            other = CompletableFuture.supplyAsync(() -> {
                started.countDown();
                return next.get();
            });
            await(started);
            assertThatThrownBy(() -> other.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        } finally {
            commit.countDown();
            voting.get(10, TimeUnit.SECONDS);
        }
        return other.get(10, TimeUnit.SECONDS);
    }

    private PollView vote(User user) {
        return pollService.vote(trip.getId(), user.getId(), poll.pollId(), poll.candidates().get(0).id());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private User newUser() {
        String key = UUID.randomUUID().toString();
        User user = users.save(User.builder().googleSub("sub-poll-concurrency-" + key)
                .email(key + "@test.com").name("테스트").build());
        createdUsers.add(user);
        return user;
    }
}
