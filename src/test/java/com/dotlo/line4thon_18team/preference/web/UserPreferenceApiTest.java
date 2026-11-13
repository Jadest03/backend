package com.dotlo.line4thon_18team.preference.web;

import com.dotlo.line4thon_18team.preference.UserPreferenceService;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 사용자 기본 취향 API. MySQL이 필요하다 (docker compose up -d).
 * 저장이 테스트 트랜잭션에 참여해서 테스트가 끝나면 되돌아간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class UserPreferenceApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @MockitoSpyBean UserPreferenceService userPreferenceService;

    private Long userId;

    @BeforeEach
    void setUp() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        userId = users.save(User.builder().googleSub("sub-pref-" + id).email(id + "@test.com").name("취향").build())
                .getId();
    }

    @Test
    void 고른_적이_없으면_기본값을_돌려준다() throws Exception {
        mvc.perform(get("/api/users/me/preferences").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.preferences.pace").value("NORMAL"))
                .andExpect(jsonPath("$.chips").isArray())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void 저장하고_다시_조회하면_그대로_나온다() throws Exception {
        save("""
                {"pace": "RELAXED", "walking": "LESS", "dayStart": "LATE",
                 "interests": ["CAFE", "NATURE"], "avoid": [], "notes": ["아이와 함께라 이동이 짧으면 좋아요"],
                 "rawText": "천천히 다니고 싶어요"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.preferences.pace").value("RELAXED"))
                .andExpect(jsonPath("$.preferences.interests[0]").value("CAFE"))
                .andExpect(jsonPath("$.rawText").value("천천히 다니고 싶어요"))
                .andExpect(jsonPath("$.updatedAt").exists());

        mvc.perform(get("/api/users/me/preferences").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.preferences.walking").value("LESS"))
                .andExpect(jsonPath("$.preferences.notes[0]").value("아이와 함께라 이동이 짧으면 좋아요"));
    }

    @Test
    void 다시_저장하면_덮어쓴다() throws Exception {
        save("""
                {"pace": "PACKED", "walking": "MORE", "dayStart": "EARLY",
                 "interests": ["FOOD"], "avoid": [], "notes": [], "rawText": "많이 보고 싶어요"}""");

        save("""
                {"pace": "RELAXED", "walking": "NORMAL", "dayStart": "NORMAL",
                 "interests": [], "avoid": [], "notes": [], "rawText": null}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.pace").value("RELAXED"))
                .andExpect(jsonPath("$.preferences.interests").isEmpty())
                .andExpect(jsonPath("$.rawText").doesNotExist());
    }

    @Test
    void 잘못된_값이면_400이다() throws Exception {
        save("""
                {"pace": "RELAXED", "walking": "NORMAL", "dayStart": "NORMAL",
                 "interests": [], "avoid": [], "notes": ["1","2","3","4","5","6"], "rawText": null}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PREFERENCE"));
    }

    @Test
    void 처음_저장이_동시에_두_번_들어와도_500이_아니다() throws Exception {
        // 다른 요청이 먼저 저장해서 이 요청이 user_id 유니크에 걸린 상황. 두 번째 시도는 실제로 저장된다
        doThrow(new DataIntegrityViolationException("uk_user_preferences_user_id"))
                .doCallRealMethod()
                .when(userPreferenceService).save(eq(userId), any());

        save("""
                {"pace": "RELAXED", "walking": "NORMAL", "dayStart": "NORMAL",
                 "interests": [], "avoid": [], "notes": [], "rawText": null}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.preferences.pace").value("RELAXED"));
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mvc.perform(get("/api/users/me/preferences"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions save(String body) throws Exception {
        return mvc.perform(put("/api/users/me/preferences")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
