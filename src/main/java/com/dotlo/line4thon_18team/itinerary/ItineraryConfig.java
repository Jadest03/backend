package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import com.dotlo.line4thon_18team.itinerary.ai.PlanEditTask;
import com.dotlo.line4thon_18team.itinerary.ai.PlanGenerationTask;
import com.dotlo.line4thon_18team.itinerary.validation.ValidationPolicy;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.place.TravelTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 일정 기능 빈 등록 (BE3).
 *
 * <p>ItineraryValidator는 장소 정보 연결(PlaceInfoProvider)이 생기면 등록한다.
 */
@Configuration
public class ItineraryConfig {

    @Bean
    public ValidationPolicy validationPolicy() {
        return ValidationPolicy.defaults();
    }

    @Bean
    public TravelTimeProvider travelTimeProvider(TravelTime travelTime, ValidationPolicy validationPolicy) {
        return new PlaceTravelTimeProvider(travelTime, validationPolicy);
    }

    @Bean
    public PlanGenerationTask planGenerationTask(SchemaLoader schemaLoader) {
        return new PlanGenerationTask(schemaLoader);
    }

    @Bean
    public PlanEditTask planEditTask(SchemaLoader schemaLoader) {
        return new PlanEditTask(schemaLoader);
    }
}
