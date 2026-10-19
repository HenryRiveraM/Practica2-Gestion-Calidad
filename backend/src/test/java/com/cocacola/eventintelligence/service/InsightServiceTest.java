package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.exception.AiProviderException;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.util.HashMap;
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

    // ---- Nuevas pruebas ----

    // Cubre líneas 51-52: catch (RuntimeException) que envuelve el error en AiProviderException
    @Test
    void shouldWrapUnexpectedRuntimeErrorFromProviderInAiProviderException() {
        Map<String, Object> analytics = analyticsData(Map.of("registered", 20L), List.of());
        IllegalStateException cause = new IllegalStateException("fallo inesperado");
        when(analyticsService.getEventInsightData(13L)).thenReturn(analytics);
        stubProviderConfigured();
        when(aiInsightProvider.generate(analytics)).thenThrow(cause);

        AiProviderException thrown =
                assertThrows(AiProviderException.class, () -> service.getEventInsights(13L));

        assertSame(cause, thrown.getCause());
        assertTrue(thrown.getMessage().contains("Gemini"));
    }

    // Cubre línea 84: return true cuando solo hay horas pico
    @Test
    void shouldGenerateInsightsWhenOnlyPeakAttendanceHoursHaveData() {
        Map<String, Object> metrics = zeroMetrics();
        metrics.put("peakAttendanceHours", List.of(Map.of("hour", 14, "attendees", 6L)));
        Map<String, Object> analytics = analyticsData(metrics, List.of());
        stubSuccessfulGeneration(14L, analytics);

        Map<String, Object> result = service.getEventInsights(14L);

        assertEquals("GENERATED", result.get("status"));
        assertEquals(true, result.get("generatedByAI"));
    }

    // Cubre rama de línea 88: activities con datos cuando todas las métricas son cero
    @Test
    void shouldGenerateInsightsWhenOnlyActivitiesHaveData() {
        Map<String, Object> analytics = analyticsData(
                zeroMetrics(),
                List.of(Map.of("type", "SAMPLING", "activity_count", 2L)));
        stubSuccessfulGeneration(15L, analytics);

        Map<String, Object> result = service.getEventInsights(15L);

        assertEquals("GENERATED", result.get("status"));
    }

    // Cubre rama de línea 67 (metrics == null) y línea 88 con activities
    @Test
    void shouldGenerateInsightsWhenMetricsAreMissingButActivitiesExist() {
        Map<String, Object> analytics = analyticsWithoutMetrics(
                List.of(Map.of("type", "SAMPLING", "activity_count", 2L)));
        stubSuccessfulGeneration(16L, analytics);

        Map<String, Object> result = service.getEventInsights(16L);

        assertEquals("GENERATED", result.get("status"));
    }

    // Cubre rama de línea 67 (metrics == null) sin activities
    @Test
    void shouldReportInsufficientDataWhenMetricsAndActivitiesAreMissing() {
        Map<String, Object> analytics = analyticsWithoutMetrics(List.of());
        when(analyticsService.getEventInsightData(17L)).thenReturn(analytics);
        stubProviderConfigured();

        Map<String, Object> result = service.getEventInsights(17L);

        assertEquals("INSUFFICIENT_DATA", result.get("status"));
        assertFalse((Boolean) result.get("generatedByAI"));
        verify(aiInsightProvider, never()).generate(org.mockito.ArgumentMatchers.anyMap());
    }

    // Cubre rama de línea 78: valor no numérico se ignora y se evalúa el siguiente campo
    @Test
    void shouldIgnoreNonNumericCountsAndUseNextNumericMetric() {
        Map<String, Object> metrics = zeroMetrics();
        metrics.put("registered", "n/a");
        metrics.put("attendees", 5L);
        Map<String, Object> analytics = analyticsData(metrics, List.of());
        stubSuccessfulGeneration(18L, analytics);

        Map<String, Object> result = service.getEventInsights(18L);

        assertEquals("GENERATED", result.get("status"));
    }

    // Cubre rama de línea 83: peakAttendanceHours no es una lista
    @Test
    void shouldReportInsufficientDataWhenPeakHoursIsNotAList() {
        Map<String, Object> metrics = zeroMetrics();
        metrics.put("peakAttendanceHours", "sin datos");
        Map<String, Object> analytics = analyticsData(metrics, List.of());
        when(analyticsService.getEventInsightData(19L)).thenReturn(analytics);
        stubProviderConfigured();

        Map<String, Object> result = service.getEventInsights(19L);

        assertEquals("INSUFFICIENT_DATA", result.get("status"));
        verify(aiInsightProvider, never()).generate(org.mockito.ArgumentMatchers.anyMap());
    }

    // ---- Helpers ----

    private void stubProviderConfigured() {
        when(aiInsightProvider.providerName()).thenReturn("gemini");
        when(aiInsightProvider.modelName()).thenReturn("gemini-3.5-flash-lite");
        when(aiInsightProvider.isConfigured()).thenReturn(true);
    }

    private void stubSuccessfulGeneration(long eventId, Map<String, Object> analytics) {
        InsightReport report = new InsightReport(
                "Resumen.",
                List.of("Insight."),
                List.of(),
                List.of("Recomendación."));
        when(analyticsService.getEventInsightData(eventId)).thenReturn(analytics);
        stubProviderConfigured();
        when(aiInsightProvider.generate(analytics)).thenReturn(report);
    }

    private static Map<String, Object> zeroMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("registered", 0L);
        metrics.put("attendees", 0L);
        metrics.put("attendance_rate", null);
        metrics.put("conversions", 0L);
        metrics.put("conversion_rate", null);
        metrics.put("new_participants", 0L);
        metrics.put("recurrent_participants", 0L);
        metrics.put("average_rating", null);
        metrics.put("nps", null);
        metrics.put("engaged_participants", 0L);
        metrics.put("engagement_rate", null);
        return metrics;
    }

    private static Map<String, Object> analyticsWithoutMetrics(List<Map<String, Object>> activities) {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("event", Map.of("id", 9L, "name", "Evento real", "event_date", "2026-10-03"));
        analytics.put("metrics", null);
        analytics.put("activities", activities);
        return analytics;
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