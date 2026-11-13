package com.dotlo.line4thon_18team.user;

import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마이: 프로필 수정, 닉네임 중복 확인, 통계. MySQL이 필요하다 (docker compose up -d).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository tripMembers;
    @Autowired SavedPlaceRepository savedPlaces;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = users.save(newUser());
        other = users.save(newUser());
    }

    @Test
    void 프로필을_고치면_내_정보에_보인다() throws Exception {
        updateProfile(me, "예빈이", "안녕하세요!", 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("예빈이"));

        mvc.perform(get("/api/users/me").header("X-User-Id", me.getId()))
                .andExpect(jsonPath("$.nickname").value("예빈이"))
                .andExpect(jsonPath("$.bio").value("안녕하세요!"))
                .andExpect(jsonPath("$.characterId").value(3));
    }

    @Test
    void 닉네임_확인은_남이_쓰면_불가_내_닉네임이면_가능() throws Exception {
        checkNickname(me, "하늘").andExpect(jsonPath("$.available").value(true));

        updateProfile(other, "하늘", null, 1).andExpect(status().isOk());

        checkNickname(me, "하늘").andExpect(jsonPath("$.available").value(false));
        checkNickname(other, "하늘").andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 남이_쓰는_닉네임으로_고치면_409() throws Exception {
        updateProfile(other, "하늘", null, 1).andExpect(status().isOk());

        updateProfile(me, "하늘", null, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
    }

    @Test
    void 닉네임은_한글_자음모음_영문_숫자_2에서_12자() throws Exception {
        checkNickname(me, "ㅋㅋ").andExpect(status().isOk());
        checkNickname(me, "Yebin5929").andExpect(status().isOk());

        for (String wrong : new String[]{"예빈!!", "하", "예 빈", "열세글자가넘는닉네임입니다"}) {
            checkNickname(me, wrong)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        }
    }

    @Test
    void 한줄소개가_30자를_넘거나_캐릭터가_1에서_10이_아니면_400() throws Exception {
        updateProfile(me, "예빈이", "가".repeat(31), 1).andExpect(status().isBadRequest());
        updateProfile(me, "예빈이", null, 11).andExpect(status().isBadRequest());
    }

    @Test
    void 통계는_내가_만든_여행과_참여한_여행을_센다() throws Exception {
        LocalDate past = LocalDate.of(2025, 1, 10);
        LocalDate future = LocalDate.of(2027, 1, 10);
        Trip endedMine = trips.save(Trip.create(me, "오사카", past, past.plusDays(2), Companion.ALONE, null));
        Trip joined = trips.save(Trip.create(other, "교토", future, future.plusDays(1), Companion.FRIEND, null));
        Trip notMine = trips.save(Trip.create(other, "도쿄", past, past.plusDays(1), Companion.ALONE, null));
        tripMembers.save(TripMember.create(joined, me));
        savePlace(endedMine);
        savePlace(endedMine);
        savePlace(joined);
        savePlace(notMine);

        mvc.perform(get("/api/users/me/stats").header("X-User-Id", me.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedPlaceCount").value(3))
                .andExpect(jsonPath("$.savedCourseCount").value(0))
                .andExpect(jsonPath("$.completedTripCount").value(1));
    }

    private ResultActions updateProfile(User user, String nickname, String bio, int characterId) throws Exception {
        String body = """
                {"nickname": "%s", "bio": %s, "characterId": %d}"""
                .formatted(nickname, bio == null ? "null" : "\"" + bio + "\"", characterId);
        return mvc.perform(patch("/api/users/me").header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions checkNickname(User user, String nickname) throws Exception {
        return mvc.perform(get("/api/users/nickname/check").param("nickname", nickname)
                .header("X-User-Id", user.getId()));
    }

    private void savePlace(Trip trip) {
        savedPlaces.save(SavedPlace.fromTakeout(trip, null, "장소", null, null, null, null, "리스트"));
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-user-" + id).email(id + "@test.com").name("테스트").build();
    }
}
