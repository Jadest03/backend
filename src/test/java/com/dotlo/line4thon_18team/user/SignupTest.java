package com.dotlo.line4thon_18team.user;

import com.dotlo.line4thon_18team.auth.google.GoogleOAuthClient;
import com.dotlo.line4thon_18team.auth.google.GoogleUser;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 흐름: 구글 로그인 → 가입 마무리(약관 + 닉네임 + 캐릭터). MySQL이 필요하다 (docker compose up -d).
 * 구글 로그인은 가짜로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class SignupTest {

    private static final String ALL_AGREED = """
            {"terms": true, "privacy": true, "age14": true, "location": false}""";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @MockitoBean GoogleOAuthClient googleOAuthClient;

    @Test
    void 처음_로그인하면_가입_전이고_가입을_마치면_다음_로그인부터_가입_완료() throws Exception {
        String sub = "sub-signup-" + UUID.randomUUID().toString().substring(0, 8);
        when(googleOAuthClient.login("code")).thenReturn(new GoogleUser(sub, sub + "@test.com", "구글이름", null, null));

        login().andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.signupCompleted").value(false));
        User user = users.findByGoogleSub(sub).orElseThrow();

        signup(user, ALL_AGREED, "하늘")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("하늘"))
                .andExpect(jsonPath("$.characterId").value(1))
                .andExpect(jsonPath("$.signupCompleted").value(true));

        login().andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.signupCompleted").value(true));
    }

    @Test
    void 필수_약관에_하나라도_동의하지_않으면_400() throws Exception {
        User user = users.save(newUser());

        signup(user, """
                {"terms": true, "privacy": true, "age14": false, "location": true}""", "하늘")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AGREEMENT_REQUIRED"));
    }

    @Test
    void 이미_가입했으면_409() throws Exception {
        User user = users.save(newUser());
        signup(user, ALL_AGREED, "하늘").andExpect(status().isOk());

        signup(user, ALL_AGREED, "바다")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SIGNED_UP"));
    }

    @Test
    void 남이_쓰는_닉네임이면_409_형식이_틀리면_400() throws Exception {
        User other = users.save(newUser());
        User user = users.save(newUser());
        signup(other, ALL_AGREED, "하늘").andExpect(status().isOk());

        signup(user, ALL_AGREED, "하늘")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
        signup(user, ALL_AGREED, "하늘!!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
    }

    private ResultActions login() throws Exception {
        return mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"code\"}"));
    }

    private ResultActions signup(User user, String agreements, String nickname) throws Exception {
        return mvc.perform(post("/api/users/me/signup").header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"agreements": %s, "nickname": "%s", "characterId": 1}""".formatted(agreements, nickname)));
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-signup-" + id).email(id + "@test.com").name("테스트").build();
    }
}
