package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.exception.AiProviderException;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {
    @Mock
    private AnalyticsOverviewService analyticsService;

    @Mock
    private AiInsightProvider aiInsightProvider;

    @InjectMocks
    private InsightService service;

    @Test
    void shouldReturnRealGeminiReportAfterProviderSuccessfullyGeneratesIt() {
        Map<String, Object> analytics = analyticsData(
                Map.of("registered", 20L, "attendance_rate", 75.0),
                List.of(Map.of("type", "SAMPLING", "activity_count", 2L)));
        InsightReport report = new InsightReport(
                "Rendimiento del evento.",
                List.of("La asistencia registrada fue 75%."),
                List.of(),
                List.of("Revisar el desempeño de la convocatoria."));
        when(analyticsService.getEventInsightData(9L)).thenReturn(analytics);
        when(aiInsightProvider.providerName()).thenReturn("gemini");
        when(aiInsightProvider.modelName()).thenReturn("gemini-3.5-flash-lite");
        when(aiInsightProvider.isConfigured()).thenReturn(true);
        when(aiInsightProvider.generate(analytics)).thenReturn(report);

        Map<String, Object> result = service.getEventInsights(9L);

        assertEquals(9L, result.get("eventId"));
        assertEquals(true, result.get("generatedByAI"));
        assertEquals("GENERATED", result.get("status"));
        assertEquals("gemini", result.get("provider"));
        assertEquals("gemini-3.5-flash-lite", result.get("model"));
        assertEquals("Rendimiento del evento.", result.get("summary"));
        assertEquals(report.insights(), result.get("insights"));
        assertEquals(report.anomalies(), result.get("anomalies"));
        assertEquals(report.recommendations(), result.get("recommendations"));
        verify(aiInsightProvider).generate(analytics);
    }

    @Test
    void shouldNotCallProviderOrReturnGeneratedContentWhenApiKeyIsMissing() {
        Map<String, Object> analytics = analyticsData(Map.of("registered", 20L), List.of());
        when(analyticsService.getEventInsightData(10L)).thenReturn(analytics);
        when(aiInsightProvider.providerName()).thenReturn("gemini");
        when(aiInsightProvider.modelName()).thenReturn("gemini-3.5-flash-lite");
        when(aiInsightProvider.isConfigured()).thenReturn(false);

        Map<String, Object> result = service.getEventInsights(10L);

        assertEquals(false, result.get("generatedByAI"));
        assertEquals("NOT_CONFIGURED", result.get("status"));
        assertEquals(null, result.get("summary"));
        assertEquals(List.of(), result.get("insights"));
        assertTrue(((String) result.get("message")).contains("GEMINI_API_KEY"));
        verify(aiInsightProvider, never()).generate(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void shouldNotCallProviderWhenMetricsAreInsufficient() {
        Map<String, Object> emptyMetrics = new LinkedHashMap<>();
        emptyMetrics.put("registered", 0L);
        emptyMetrics.put("attendees", 0L);
        emptyMetrics.put("attendance_rate", null);
        emptyMetrics.put("conversions", 0L);
        emptyMetrics.put("conversion_rate", null);
        emptyMetrics.put("new_participants", 0L);
        emptyMetrics.put("recurrent_participants", 0L);
        emptyMetrics.put("average_rating", null);
        emptyMetrics.put("nps", null);
        emptyMetrics.put("engaged_participants", 0L);
        emptyMetrics.put("engagement_rate", null);
        emptyMetrics.put("peakAttendanceHours", List.of());
        when(analyticsService.getEventInsightData(11L)).thenReturn(analyticsData(emptyMetrics, List.of()));
        when(aiInsightProvider.providerName()).thenReturn("gemini");
        when(aiInsightProvider.modelName()).thenReturn("gemini-3.5-flash-lite");
        when(aiInsightProvider.isConfigured()).thenReturn(true);

        Map<String, Object> result = service.getEventInsights(11L);

        assertEquals(false, result.get("generatedByAI"));
        assertEquals("INSUFFICIENT_DATA", result.get("status"));
        assertEquals(List.of(), result.get("recommendations"));
        verify(aiInsightProvider).isConfigured();
        verify(aiInsightProvider, never()).generate(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void shouldPropagateNotFoundWhenEventDoesNotExist() {
        when(analyticsService.getEventInsightData(404L))
                .thenThrow(new ResourceNotFoundException("Evento", 404L));

        assertThrows(ResourceNotFoundException.class, () -> service.getEventInsights(404L));
        verify(aiInsightProvider, never()).generate(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void shouldPropagateProviderErrorWithoutReturningSuccessShapedResponse() {
        Map<String, Object> analytics = analyticsData(Map.of("registered", 20L), List.of());
        when(analyticsService.getEventInsightData(12L)).thenReturn(analytics);
        when(aiInsightProvider.providerName()).thenReturn("gemini");
        when(aiInsightProvider.modelName()).thenReturn("gemini-3.5-flash-lite");
        when(aiInsightProvider.isConfigured()).thenReturn(true);
        when(aiInsightProvider.generate(analytics)).thenThrow(new AiProviderException("Gemini no respondió."));

        assertThrows(AiProviderException.class, () -> service.getEventInsights(12L));
    }

    private static Map<String, Object> analyticsData(
            Map<String, Object> metrics,
            List<Map<String, Object>> activities) {
        return Map.of(
                "event", Map.of("id", 9L, "name", "Evento real", "event_date", "2026-10-03"),
                "metrics", metrics,
                "activities", activities);
    }
}
