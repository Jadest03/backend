package com.dotlo.line4thon_18team.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 브라우저가 실제 요청 전에 보내는 사전 요청(OPTIONS)으로 CORS 허용 여부를 확인한다. MySQL이 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:5173, https://dotlo-*-team.vercel.app")
class CorsConfigTest {

    @Autowired MockMvc mvc;

    @Test
    void 허용한_주소의_요청은_통과한다() throws Exception {
        preflight("http://localhost:5173")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void 패턴에_맞는_Vercel_Preview_주소도_통과한다() throws Exception {
        preflight("https://dotlo-abc123-team.vercel.app")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://dotlo-abc123-team.vercel.app"));
    }

    @Test
    void 허용하지_않은_주소는_막는다() throws Exception {
        preflight("https://evil.example.com")
                .andExpect(status().isForbidden());
    }

    // FE 토큰 재발급이 401 응답을 읽어야 해서, 401 에도 CORS 헤더가 붙어야 한다
    @Test
    void 토큰이_없어_401이_나도_CORS_헤더가_붙는다() throws Exception {
        mvc.perform(get("/api/trips").header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void 잘못된_토큰으로_401이_나도_CORS_헤더가_붙는다() throws Exception {
        mvc.perform(get("/api/trips")
                        .header("Origin", "http://localhost:5173")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private ResultActions preflight(String origin) throws Exception {
        return mvc.perform(options("/api/trips")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "Authorization"));
    }
}
