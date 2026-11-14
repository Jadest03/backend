package com.dotlo.line4thon_18team.chat.poll;

import com.dotlo.line4thon_18team.chat.poll.ai.PollPickTask;
import com.dotlo.line4thon_18team.chat.poll.ai.PollSearchTask;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 투표 후보 만들기 빈 등록 (BE3). */
@Configuration
public class PollAiConfig {

    @Bean
    public PollSearchTask pollSearchTask(SchemaLoader schemaLoader) {
        return new PollSearchTask(schemaLoader);
    }

    @Bean
    public PollPickTask pollPickTask(SchemaLoader schemaLoader) {
        return new PollPickTask(schemaLoader);
    }
}
