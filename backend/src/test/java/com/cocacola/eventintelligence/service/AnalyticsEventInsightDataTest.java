package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventInsightDataTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private AnalyticsOverviewService service;

    @Test
    void shouldAssembleEventInsightsFromRealAnalyticsAndActivityQueries() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("id", 7L, "name", "Evento real", "event_date", "2026-10-03",
                        "location", "La Paz", "campaign_name", "Campaña real")))
                .thenReturn(List.of(Map.of("hour", 18, "attendees", 6L)))
                .thenReturn(List.of(Map.of("type", "SAMPLING", "activity_count", 2L,
                        "total_quantity", 30L, "participants", 10L)));
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class)))
                .thenReturn(Map.of(
                        "registered", 10L,
                        "attendees", 6L,
                        "attendance_rate", 60.0,
                        "conversions", 2L,
                        "conversion_rate", 33.3,
                        "new_participants", 8L,
                        "recurrent_participants", 2L,
                        "nps", 40.0,
                        "average_rating", 4.5))
                .thenReturn(Map.of("engaged_participants", 4L, "engagement_rate", 66.7));

        Map<String, Object> result = service.getEventInsightData(7L);

        Map<String, Object> metrics = objectMap(result, "metrics");
        assertEquals(7L, objectMap(result, "event").get("id"));
        assertEquals(60.0, metrics.get("attendance_rate"));
        assertEquals(40.0, metrics.get("nps"));
        assertEquals(66.7, metrics.get("engagement_rate"));
        assertEquals(18, objectList(metrics, "peakAttendanceHours").getFirst().get("hour"));
        assertEquals("SAMPLING", objectList(result, "activities").getFirst().get("type"));
    }

    @Test
    void shouldStopWhenEventDoesNotExist() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.getEventInsightData(404L));
        verify(jdbcTemplate, never()).queryForMap(anyString(), any(SqlParameterSource.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objectList(Map<String, Object> source, String key) {
        return (List<Map<String, Object>>) source.get(key);
    }
}
