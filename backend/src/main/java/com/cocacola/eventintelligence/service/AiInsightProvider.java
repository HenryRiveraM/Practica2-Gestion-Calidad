package com.cocacola.eventintelligence.service;

import java.util.Map;

public interface AiInsightProvider {
    boolean isConfigured();

    InsightReport generate(Map<String, Object> eventAnalytics);

    String providerName();

    String modelName();
}
