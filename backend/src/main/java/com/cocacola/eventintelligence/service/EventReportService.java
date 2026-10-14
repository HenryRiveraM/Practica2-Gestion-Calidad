package com.cocacola.eventintelligence.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EventReportService {
    private static final String CSV_HEADER = "Sección,Indicador,Valor";

    private final AnalyticsOverviewService analyticsService;
    private final EventImpactScoreService impactScoreService;

    public EventReportService(
            AnalyticsOverviewService analyticsService,
            EventImpactScoreService impactScoreService) {
        this.analyticsService = analyticsService;
        this.impactScoreService = impactScoreService;
    }

    public byte[] generateEventCsv(long eventId) {
        Map<String, Object> analytics = analyticsService.getEventInsightData(eventId);
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) analytics.get("event");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) analytics.get("metrics");
        Map<String, Object> impactScore = impactScoreService.getImpactScore(eventId);

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Evento", "ID", value(event.get("id"))));
        rows.add(List.of("Evento", "Nombre", value(event.get("name"))));
        rows.add(List.of("Evento", "Fecha", value(event.get("event_date"))));
        rows.add(List.of("Evento", "Ubicación", value(event.get("location"))));
        rows.add(List.of("Evento", "Campaña", value(event.get("campaign_name"))));
        rows.add(List.of("Participación", "Participantes registrados", value(metrics.get("registered"))));
        rows.add(List.of("Participación", "Asistentes", value(metrics.get("attendees"))));
        rows.add(List.of("Participación", "Tasa de asistencia (%)", value(metrics.get("attendance_rate"))));
        rows.add(List.of("Conversión", "Conversiones", value(metrics.get("conversions"))));
        rows.add(List.of("Conversión", "Tasa de conversión (%)", value(metrics.get("conversion_rate"))));
        rows.add(List.of("Satisfacción", "Rating promedio", value(metrics.get("average_rating"))));
        rows.add(List.of("Satisfacción", "NPS", value(metrics.get("nps"))));
        rows.add(List.of("Participantes", "Participantes nuevos", value(metrics.get("new_participants"))));
        rows.add(List.of("Participantes", "Participantes recurrentes", value(metrics.get("recurrent_participants"))));
        rows.add(List.of("Impacto", "Event Impact Score", value(impactScore.get("score"))));
        rows.add(List.of("Impacto", "Estado del score", value(impactScore.get("status"))));

        StringBuilder csv = new StringBuilder("\uFEFF").append(CSV_HEADER).append("\r\n");
        rows.forEach(row -> csv.append(row.stream().map(EventReportService::escapeCsv)
                .reduce((left, right) -> left + "," + right).orElse(""))
                .append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String value(Object value) {
        return value == null ? "Sin datos" : value.toString();
    }

    private static String escapeCsv(String value) {
        String safeValue = value;
        boolean negativeNumber = safeValue.matches("-\\d+(?:\\.\\d+)?");
        if ((!safeValue.isEmpty() && "=+@".indexOf(safeValue.charAt(0)) >= 0)
                || (safeValue.startsWith("-") && !negativeNumber)) {
            safeValue = "'" + safeValue;
        }
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }
}
