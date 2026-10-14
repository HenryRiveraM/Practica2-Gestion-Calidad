package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventReportServiceTest {
    @Mock
    private AnalyticsOverviewService analyticsService;

    @Mock
    private EventImpactScoreService impactScoreService;

    @InjectMocks
    private EventReportService service;

    @Test
    void generatesCsvFromAnalyticsAndImpactScoreServices() {
        Map<String, Object> analytics = eventData(
                Map.of(
                        "registered", 100L,
                        "attendees", 80L,
                        "attendance_rate", 80.0,
                        "conversions", 20L,
                        "conversion_rate", 25.0,
                        "average_rating", 4.5,
                        "nps", 40.0,
                        "new_participants", 70L,
                        "recurrent_participants", 10L));
        when(analyticsService.getEventInsightData(15L)).thenReturn(analytics);
        when(impactScoreService.getImpactScore(15L)).thenReturn(Map.of("score", 82.4, "status", "CALCULATED"));

        String csv = new String(service.generateEventCsv(15L), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFFSección,Indicador,Valor\r\n"));
        assertTrue(csv.contains("\"Evento\",\"Nombre\",\"Encuentro, Coca-Cola\""));
        assertTrue(csv.contains("\"Participación\",\"Tasa de asistencia (%)\",\"80.0\""));
        assertTrue(csv.contains("\"Conversión\",\"Conversiones\",\"20\""));
        assertTrue(csv.contains("\"Satisfacción\",\"Rating promedio\",\"4.5\""));
        assertTrue(csv.contains("\"Satisfacción\",\"NPS\",\"40.0\""));
        assertTrue(csv.contains("\"Participantes\",\"Participantes nuevos\",\"70\""));
        assertTrue(csv.contains("\"Participantes\",\"Participantes recurrentes\",\"10\""));
        assertTrue(csv.contains("\"Impacto\",\"Event Impact Score\",\"82.4\""));
        verify(analyticsService).getEventInsightData(15L);
        verify(impactScoreService).getImpactScore(15L);
    }

    @Test
    void exportsExplicitNoDataValuesForEventWithoutMetrics() {
        Map<String, Object> emptyMetrics = new HashMap<>();
        emptyMetrics.put("registered", 0L);
        emptyMetrics.put("attendees", 0L);
        emptyMetrics.put("attendance_rate", null);
        emptyMetrics.put("conversions", 0L);
        emptyMetrics.put("conversion_rate", null);
        emptyMetrics.put("average_rating", null);
        emptyMetrics.put("nps", null);
        emptyMetrics.put("new_participants", 0L);
        emptyMetrics.put("recurrent_participants", 0L);
        when(analyticsService.getEventInsightData(16L)).thenReturn(eventData(emptyMetrics));
        Map<String, Object> emptyScore = new HashMap<>();
        emptyScore.put("score", null);
        emptyScore.put("status", "INSUFFICIENT_DATA");
        when(impactScoreService.getImpactScore(16L)).thenReturn(emptyScore);

        String csv = new String(service.generateEventCsv(16L), StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"Participación\",\"Tasa de asistencia (%)\",\"Sin datos\""));
        assertTrue(csv.contains("\"Satisfacción\",\"Rating promedio\",\"Sin datos\""));
        assertTrue(csv.contains("\"Impacto\",\"Event Impact Score\",\"Sin datos\""));
    }

    @Test
    void propagatesNotFoundForUnknownEvent() {
        when(analyticsService.getEventInsightData(999L))
                .thenThrow(new ResourceNotFoundException("Evento", 999L));

        assertThrows(ResourceNotFoundException.class, () -> service.generateEventCsv(999L));
    }

    private static Map<String, Object> eventData(Map<String, Object> metrics) {
        return Map.of(
                "event", Map.of(
                        "id", 15L,
                        "name", "Encuentro, Coca-Cola",
                        "event_date", "2026-10-03",
                        "location", "La Paz",
                        "campaign_name", "Campaña otoño"),
                "metrics", metrics,
                "activities", List.of());
    }

}
