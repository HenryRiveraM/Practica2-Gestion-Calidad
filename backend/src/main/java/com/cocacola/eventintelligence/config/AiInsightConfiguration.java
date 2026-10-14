package com.cocacola.eventintelligence.config;

import com.cocacola.eventintelligence.service.AiInsightProvider;
import com.cocacola.eventintelligence.service.UnavailableAiInsightProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiInsightConfiguration {
    @Bean
    @ConditionalOnMissingBean(AiInsightProvider.class)
    AiInsightProvider unavailableAiInsightProvider() {
        return new UnavailableAiInsightProvider();
    }
}
