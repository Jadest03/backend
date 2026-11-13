package com.dotlo.line4thon_18team.member;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.member.entity.TripInvite;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripInviteRepository;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.member.service.InviteService;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마지막 한 자리에 두 명이 동시에 수락하는 경우. MySQL이 필요하다 (docker compose up -d).
 * 수락마다 트랜잭션이 따로 열려야 해서 테스트 트랜잭션 없이 돌리고, 만든 데이터는 테스트 끝에 지운다.
 */
@SpringBootTest
class InviteAcceptRaceTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Autowired InviteService inviteService;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository tripMembers;
    @Autowired TripInviteRepository tripInvites;

    private final List<User> created = new ArrayList<>();
    private Trip trip;
    private TripInvite invite;

    @BeforeEach
    void setUp() {
        trip = trips.save(Trip.create(newUser(), "오사카", START, START.plusDays(2), Companion.FRIEND, null));
        for (int i = 0; i < 8; i++) {   // 방장 포함 9명, 한 자리 남음
            tripMembers.save(TripMember.create(trip, newUser()));
        }
        invite = tripInvites.save(TripInvite.create(trip, "race-" + UUID.randomUUID().toString().substring(0, 8),
                trip.getUser().getId(), LocalDateTime.now().plusDays(1)));
    }

    @AfterEach
    void tearDown() {
        tripMembers.deleteAll(tripMembers.findAllByTripIdOrderByIdAsc(trip.getId()));
        tripInvites.delete(invite);
        trips.delete(trip);
        users.deleteAll(created);
    }

    @Test
    void 마지막_한_자리에_두_명이_동시에_수락해도_한_명만_들어간다() throws Exception {
        List<Long> joiners = List.of(newUser().getId(), newUser().getId());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Long>> results = joiners.stream()
                .map(userId -> pool.submit(() -> {
                    start.await();
                    return inviteService.accept(invite.getToken(), userId);
                }))
                .toList();
        start.countDown();

        int full = 0;
        for (Future<Long> result : results) {
            try {
                result.get();
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.TRIP_MEMBER_FULL);
                full++;
            }
        }
        pool.shutdown();

        assertThat(full).isEqualTo(1);
        assertThat(tripMembers.countByTripId(trip.getId())).isEqualTo(9);
    }

    private User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(User.builder().googleSub("sub-" + id).email(id + "@test.com").name("테스트").build());
        created.add(user);
        return user;
    }
}
