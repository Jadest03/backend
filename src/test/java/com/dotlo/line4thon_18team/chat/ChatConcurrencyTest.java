package com.dotlo.line4thon_18team.chat;

import com.dotlo.line4thon_18team.chat.dto.ChatMessageResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메시지 저장이 다른 요청과 겹칠 때: 먼저 id 를 받은 메시지가 늦게 커밋돼도 폴링에서 빠지지 않는지,
 * 저장 도중에 여행을 지워도 외래키 오류 없이 지워지는지.
 * 요청마다 트랜잭션이 따로 열려야 해서 테스트 트랜잭션 없이 돌리고, 끝에 여행째 지운다. MySQL이 필요하다.
 */
@SpringBootTest
class ChatConcurrencyTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Autowired ChatMessageService chatMessageService;
    @Autowired TransactionTemplate transactions;
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
    }

    @AfterEach
    void tearDown() {
        if (trips.existsById(trip.getId())) {
            tripDeleteService.deleteTrip(owner.getId(), trip.getId());
        }
        users.deleteAll(created);
    }

    @Test
    void 먼저_저장을_시작한_메시지가_늦게_커밋돼도_폴링에서_빠지지_않는다() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        // 1) 도틀로 메시지가 먼저 저장을 시작하고, 커밋하지 않은 채 멈춘다
        CompletableFuture<Void> slow = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            chatMessageService.appendAi(trip.getId(), "먼저 시작한 메시지", null);
            inserted.countDown();
            await(commit);
        }));
        await(inserted);
        // 2) 그사이 사람 메시지를 보낸다 (고치기 전에는 여기서 더 큰 id 로 먼저 커밋됐다)
        CompletableFuture<ChatMessageResponse> fast = CompletableFuture.supplyAsync(
                () -> chatMessageService.send(member.getId(), trip.getId(), "나중에 보낸 메시지"));
        Thread.sleep(300);

        // 3) 이 시점에 폴링한 FE 가 받은 것 중 마지막 id 를 커서로 쓴다
        List<ChatMessageResponse> firstPoll = poll(null);
        long cursor = firstPoll.isEmpty() ? 0 : firstPoll.get(firstPoll.size() - 1).id();

        commit.countDown();
        slow.get(10, TimeUnit.SECONDS);
        fast.get(10, TimeUnit.SECONDS);

        List<String> seen = new ArrayList<>(firstPoll.stream().map(ChatMessageResponse::text).toList());
        seen.addAll(poll(cursor).stream().map(ChatMessageResponse::text).toList());
        assertThat(seen).containsExactly("먼저 시작한 메시지", "나중에 보낸 메시지");
    }

    @Test
    void 메시지를_저장하는_도중에_여행을_지워도_외래키_오류_없이_지워진다() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        // 1) 메시지 저장이 시작되고, 커밋하지 않은 채 멈춘다
        CompletableFuture<Void> sending = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            chatMessageService.send(member.getId(), trip.getId(), "지우는 중에 보낸 메시지");
            inserted.countDown();
            await(commit);
        }));
        await(inserted);
        // 2) 그사이 방장이 여행을 지운다 (고치기 전에는 메시지를 못 보고 정리한 뒤 여행 삭제에서 외래키 오류)
        CompletableFuture<Void> deleting = CompletableFuture.runAsync(
                () -> tripDeleteService.deleteTrip(owner.getId(), trip.getId()));
        Thread.sleep(300);

        commit.countDown();
        sending.get(10, TimeUnit.SECONDS);
        deleting.get(10, TimeUnit.SECONDS);
        assertThat(trips.existsById(trip.getId())).isFalse();
    }

    private List<ChatMessageResponse> poll(Long after) {
        return chatMessageService.list(owner.getId(), trip.getId(), after).messages();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(User.builder().googleSub("sub-order-" + id).email(id + "@test.com").name("테스트").build());
        created.add(user);
        return user;
    }
}
