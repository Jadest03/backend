package com.dotlo.line4thon_18team.chat;

import com.dotlo.line4thon_18team.chat.ai.CoordinationTask;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 채팅 AI 빈 등록 (BE3). */
@Configuration
public class ChatConfig {

    @Bean
    public CoordinationTask coordinationTask(SchemaLoader schemaLoader) {
        return new CoordinationTask(schemaLoader);
    }
}
