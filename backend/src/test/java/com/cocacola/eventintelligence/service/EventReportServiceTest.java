package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("P2-16-C01: escapa comillas dobles del nombre del evento")
    void escapesDoubleQuotesInEventName() {
        stubReportForName("Encuentro \"Coca-Cola\"");

        String csv = new String(service.generateEventCsv(15L), StandardCharsets.UTF_8);

        assertEquals("\"Evento\",\"Nombre\",\"Encuentro \"\"Coca-Cola\"\"\"",
                csv.split("\r\n")[2]);
    }

    @Test
    @DisplayName("P2-16-C02: conserva saltos de línea dentro del campo entre comillas")
    void preservesLineBreaksInsideQuotedField() {
        stubReportForName("Primera línea\r\nSegunda línea\nTercera línea");

        String csv = new String(service.generateEventCsv(15L), StandardCharsets.UTF_8);

        String expectedRecord = "\"Evento\",\"Nombre\",\"Primera línea\r\nSegunda línea\nTercera línea\"\r\n";
        int nameStart = csv.indexOf("\"Evento\",\"Nombre\",");
        int nextRecord = csv.indexOf("\"Evento\",\"Fecha\",");
        assertTrue(nameStart >= 0 && nextRecord > nameStart);
        assertEquals(expectedRecord, csv.substring(nameStart, nextRecord));
    }

    @Test
    @DisplayName("P2-16-C03: preserva acentos al decodificar bytes UTF-8")
    void preservesAccentedCharactersInUtf8() throws java.nio.charset.CharacterCodingException {
        stubReportForName("Celebración: música, café y año");

        byte[] bytes = service.generateEventCsv(15L);
        String csv = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();

        assertTrue(csv.startsWith("\uFEFFSección,Indicador,Valor\r\n"));
        assertEquals("\"Evento\",\"Nombre\",\"Celebración: música, café y año\"",
                csv.split("\r\n")[2]);
    }

    @Test
    @DisplayName("P2-16-C04: propaga error del puntaje sin retornar CSV parcial")
    void propagatesImpactScoreFailureWithoutReturningPartialReport() {
        when(analyticsService.getEventInsightData(15L)).thenReturn(eventData(Map.of()));
        var failure = new org.springframework.dao.DataAccessResourceFailureException("Puntaje no disponible");
        when(impactScoreService.getImpactScore(15L)).thenThrow(failure);

        var actual = assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> service.generateEventCsv(15L));

        assertSame(failure, actual);
        var order = org.mockito.Mockito.inOrder(analyticsService, impactScoreService);
        order.verify(analyticsService).getEventInsightData(15L);
        order.verify(impactScoreService).getImpactScore(15L);
        org.mockito.Mockito.verifyNoMoreInteractions(analyticsService, impactScoreService);
    }

    private void stubReportForName(String name) {
        Map<String, Object> analytics = new HashMap<>(eventData(Map.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> originalEvent = (Map<String, Object>) analytics.get("event");
        Map<String, Object> event = new HashMap<>(originalEvent);
        event.put("name", name);
        analytics.put("event", event);
        when(analyticsService.getEventInsightData(15L)).thenReturn(analytics);
        when(impactScoreService.getImpactScore(15L))
                .thenReturn(Map.of("score", 82.4, "status", "CALCULATED"));
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
