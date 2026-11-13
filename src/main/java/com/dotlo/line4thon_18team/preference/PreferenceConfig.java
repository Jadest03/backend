package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import com.dotlo.line4thon_18team.preference.ai.PreferenceInterpretationTask;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 취향 기능 빈 등록 (BE3).
 *
 * <p>저장소는 DB(JPA)를 쓴다. 메모리 구현(InMemoryPreferenceRepository)은 단위 테스트에서만 쓴다.
 */
@Configuration
public class PreferenceConfig {

    @Bean
    public PreferenceInterpretationTask preferenceInterpretationTask(SchemaLoader schemaLoader) {
        return new PreferenceInterpretationTask(schemaLoader);
    }

    @Bean
    public PreferenceRepository preferenceRepository(PreferenceEntityRepository preferenceEntityRepository) {
        return new JpaPreferenceRepository(preferenceEntityRepository);
    }

    @Bean
    public UserPreferenceRepository userPreferenceRepository(UserPreferenceEntityRepository entities,
                                                             UserRepository users) {
        return new JpaUserPreferenceRepository(entities, users);
    }

    @Bean
    public UserPreferenceService userPreferenceService(UserPreferenceRepository userPreferenceRepository) {
        return new UserPreferenceService(userPreferenceRepository, Clock.systemUTC());
    }

    @Bean
    public PreferenceService preferenceService(PreferenceRepository preferenceRepository,
                                               UserPreferenceService userPreferenceService,
                                               StructuredLlmExecutor structuredLlmExecutor,
                                               PreferenceInterpretationTask preferenceInterpretationTask) {
        return new PreferenceService(preferenceRepository, userPreferenceService, structuredLlmExecutor,
                preferenceInterpretationTask, Clock.systemUTC());
    }
}
