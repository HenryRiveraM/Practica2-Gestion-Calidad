package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.exception.AiProviderException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InsightService {
    private final AnalyticsOverviewService analyticsService;
    private final AiInsightProvider aiInsightProvider;

    public InsightService(AnalyticsOverviewService analyticsService, AiInsightProvider aiInsightProvider) {
        this.analyticsService = analyticsService;
        this.aiInsightProvider = aiInsightProvider;
    }

    public Map<String, Object> getEventInsights(long eventId) {
        Map<String, Object> analytics = analyticsService.getEventInsightData(eventId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("eventId", eventId);
        response.put("generatedByAI", false);
        response.put("provider", aiInsightProvider.providerName());
        response.put("model", aiInsightProvider.modelName());

        if (!aiInsightProvider.isConfigured()) {
            response.put("status", "NOT_CONFIGURED");
            response.put("message", "La IA no está configurada. Define GEMINI_API_KEY en el entorno del backend.");
            response.put("summary", null);
            response.put("insights", List.of());
            response.put("anomalies", List.of());
            response.put("recommendations", List.of());
            return response;
        }
        if (!hasSufficientData(analytics)) {
            response.put("status", "INSUFFICIENT_DATA");
            response.put("message", "No hay datos suficientes del evento para generar insights.");
            response.put("summary", null);
            response.put("insights", List.of());
            response.put("anomalies", List.of());
            response.put("recommendations", List.of());
            return response;
        }

        InsightReport report;
        try {
            report = aiInsightProvider.generate(analytics);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiProviderException("No se pudieron generar insights con Gemini.", ex);
        }

        response.put("generatedByAI", true);
        response.put("status", "GENERATED");
        response.put("summary", report.summary());
        response.put("insights", report.insights());
        response.put("anomalies", report.anomalies());
        response.put("recommendations", report.recommendations());
        return response;
    }

    private static boolean hasSufficientData(Map<String, Object> analytics) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) analytics.get("metrics");
        if (metrics != null) {
            for (String key : List.of(
                    "attendance_rate", "conversion_rate", "average_rating", "nps", "engagement_rate")) {
                if (metrics.get(key) != null) {
                    return true;
                }
            }
            for (String key : List.of(
                    "registered", "attendees", "conversions", "new_participants", "recurrent_participants",
                    "engaged_participants")) {
                Object value = metrics.get(key);
                if (value instanceof Number number && number.longValue() > 0) {
                    return true;
                }
            }
            Object peakHours = metrics.get("peakAttendanceHours");
            if (peakHours instanceof List<?> hours && !hours.isEmpty()) {
                return true;
            }
        }
        Object activities = analytics.get("activities");
        return activities instanceof List<?> activityRows && !activityRows.isEmpty();
    }
}
