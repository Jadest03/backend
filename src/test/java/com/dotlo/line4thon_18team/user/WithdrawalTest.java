package com.dotlo.line4thon_18team.user;

import com.dotlo.line4thon_18team.auth.google.GoogleOAuthClient;
import com.dotlo.line4thon_18team.auth.google.GoogleUser;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.preference.PreferenceRepository;
import com.dotlo.line4thon_18team.preference.StoredPreference;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 탈퇴: 탈퇴 전 안내, 탈퇴 후 여행 처리와 개인정보 삭제. MySQL이 필요하다 (docker compose up -d).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class WithdrawalTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository tripMembers;
    @Autowired PreferenceRepository preferences;
    @MockitoBean GoogleOAuthClient googleOAuthClient;

    private String sub;
    private User me;
    private User friend;
    private Trip solo;
    private Trip shared;
    private Trip joined;

    @BeforeEach
    void setUp() {
        sub = "sub-withdraw-" + UUID.randomUUID().toString().substring(0, 8);
        me = users.save(User.builder().googleSub(sub).email(sub + "@test.com").name("나").build());
        me.completeSignup("탈퇴할사람", 1, false, java.time.LocalDateTime.now());
        friend = users.save(User.builder().googleSub(sub + "-f").email(sub + "-f@test.com").name("친구").build());

        solo = trips.save(Trip.create(me, "도쿄", START, START.plusDays(1), Companion.ALONE, null));
        shared = trips.save(Trip.create(me, "오사카", START, START.plusDays(2), Companion.FRIEND, null));
        tripMembers.save(TripMember.create(shared, friend));
        joined = trips.save(Trip.create(friend, "교토", START, START.plusDays(1), Companion.FRIEND, null));
        tripMembers.save(TripMember.create(joined, me));
    }

    @Test
    void 탈퇴_전에_삭제될_여행_넘어갈_여행_빠질_여행을_알려준다() throws Exception {
        mvc.perform(get("/api/users/me/withdrawal").header("X-User-Id", me.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedTrips.length()").value(1))
                .andExpect(jsonPath("$.deletedTrips[0].tripId").value(solo.getId()))
                .andExpect(jsonPath("$.transferredTrips[0].tripId").value(shared.getId()))
                .andExpect(jsonPath("$.transferredTrips[0].newOwnerName").value("친구"))
                .andExpect(jsonPath("$.leftTrips[0].tripId").value(joined.getId()));
    }

    @Test
    void 탈퇴하면_혼자_쓰던_여행은_지워지고_같이_쓰던_여행은_남는다() throws Exception {
        savePreference(shared, "해산물 알레르기가 있어요");
        savePreference(joined, "야경을 좋아해요");

        mvc.perform(delete("/api/users/me").header("X-User-Id", me.getId())).andExpect(status().isNoContent());

        assertThat(trips.findById(solo.getId())).isEmpty();
        assertThat(trips.findById(shared.getId())).get()
                .extracting(trip -> trip.getUser().getId()).isEqualTo(friend.getId());
        assertThat(tripMembers.countByTripId(shared.getId())).isZero();
        assertThat(tripMembers.existsByTripIdAndUserId(joined.getId(), me.getId())).isFalse();
        // 남는 여행에 내가 쓴 여행별 취향(메모)이 남지 않는다
        assertThat(preferences.findByTripAndUser(shared.getId(), me.getId())).isEmpty();
        assertThat(preferences.findByTripAndUser(joined.getId(), me.getId())).isEmpty();

        User withdrawn = users.findById(me.getId()).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.getNickname()).isNull();
        assertThat(withdrawn.getEmail()).isEmpty();
        assertThat(withdrawn.getDisplayName()).isEqualTo("탈퇴한 사용자");
        mvc.perform(get("/api/users/me").header("X-User-Id", me.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 탈퇴한_뒤_같은_구글_계정으로_오면_새로_가입한다() throws Exception {
        mvc.perform(delete("/api/users/me").header("X-User-Id", me.getId())).andExpect(status().isNoContent());
        when(googleOAuthClient.login("code")).thenReturn(new GoogleUser(sub, sub + "@test.com", "나", null, null));

        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"code\"}"))
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.signupCompleted").value(false));
        assertThat(users.findByGoogleSub(sub)).get().extracting(User::getId).isNotEqualTo(me.getId());
    }

    private void savePreference(Trip trip, String rawText) {
        preferences.save(new StoredPreference(trip.getId(), me.getId(), PreferenceProfile.defaults(), rawText,
                Instant.now()));
    }
}
