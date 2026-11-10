package com.dotlo.line4thon_18team.global.config;

import com.dotlo.line4thon_18team.auth.jwt.JwtAuthenticationFilter;
import com.dotlo.line4thon_18team.auth.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 보안 설정.
 * - 요청마다 access 토큰이 있으면 검사해서 사용자 id 를 기억한다. (JwtAuthenticationFilter)
 * - 로그인이 필요한지는 API 마다 @LoginUser 로 판단한다. 그래서 여기서는 모든 요청을 통과시킨다.
 *
 * @WebMvcTest 에서는 이 설정이 자동으로 안 불러와져서, 테스트에
 * @Import({SecurityConfig.class, JwtProvider.class}) 를 붙여야 한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())              // 아래 corsConfigurationSource 사용
                .csrf(csrf -> csrf.disable())                 // 토큰 방식이라 CSRF 보호 불필요
                .httpBasic(basic -> basic.disable())          // 브라우저 기본 로그인 창 끄기
                .formLogin(form -> form.disable())            // 로그인 페이지 끄기
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 세션 대신 토큰
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())            // 로그인 확인은 @LoginUser 에서
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // 프론트 주소에서 오는 요청 허용. 주소는 CORS_ALLOWED_ORIGINS (쉼표로 여러 개, Vercel Preview 는 * 패턴)
    // 토큰을 Authorization 헤더로 받아서 쿠키(credentials)는 허용하지 않는다
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
