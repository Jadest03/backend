package com.dotlo.line4thon_18team.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 서비스 시계. 날짜 계산(다녀온 여행 등)이 서버 시간대 설정과 상관없이 한국 날짜로 되게 Asia/Seoul 로 고정한다.
 * 테스트에서는 고정 시계로 바꿔서 날짜를 정할 수 있다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
