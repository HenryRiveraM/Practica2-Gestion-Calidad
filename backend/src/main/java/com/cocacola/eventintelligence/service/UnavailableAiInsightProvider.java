package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.exception.AiProviderException;
import java.util.Map;

public class UnavailableAiInsightProvider implements AiInsightProvider {
    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public InsightReport generate(Map<String, Object> eventAnalytics) {
        throw new AiProviderException("No hay un proveedor de IA configurado.");
    }

    @Override
    public String providerName() {
        return "unavailable";
    }

    @Override
    public String modelName() {
        return "none";
    }
}
