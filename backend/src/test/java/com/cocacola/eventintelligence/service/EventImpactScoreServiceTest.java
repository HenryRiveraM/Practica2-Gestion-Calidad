package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class EventImpactScoreServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private EventImpactScoreService service;

    @Test
    void calculatesImpactScoreFromRealEventMetrics() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(metrics(100, 80, 64, 4.5, 40, 20)));

        Map<String, Object> response = service.getImpactScore(12);

        assertEquals(12L, response.get("eventId"));
        assertEquals(70.0, response.get("score"));
        assertEquals("CALCULATED", response.get("status"));
        assertEquals(Map.of(
                "attendance", 80.0,
                "engagement", 80.0,
                "satisfaction", 90.0,
                "conversion", 50.0,
                "recurrence", 20.0), response.get("metrics"));
        assertEquals(EventImpactScoreWeights.WEIGHTS, response.get("weights"));
        assertTrue(((Map<?, ?>) response.get("metricDefinitions")).containsKey("engagement"));
    }

    @Test
    void reportsInsufficientDataForEventWithoutMetrics() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(metrics(0, 0, 0, null, 0, 0)));

        Map<String, Object> response = service.getImpactScore(12);

        assertNull(response.get("score"));
        assertEquals("INSUFFICIENT_DATA", response.get("status"));
        assertEquals(List.of("attendance", "engagement", "satisfaction", "conversion", "recurrence"),
                response.get("missingMetrics"));
        assertEquals(Map.of(), response.get("appliedWeights"));
    }

    @Test
    void renormalizesWeightsWhenSomeMetricsAreUnavailable() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(metrics(100, 80, 40, null, 20, 10)));

        Map<String, Object> response = service.getImpactScore(12);

        assertEquals(50.0, response.get("score"));
        assertEquals("PARTIAL_DATA", response.get("status"));
        assertEquals(List.of("satisfaction"), response.get("missingMetrics"));
        Map<?, ?> appliedWeights = (Map<?, ?>) response.get("appliedWeights");
        assertEquals(1.0, appliedWeights.values().stream().mapToDouble(value -> (Double) value).sum(), 1e-12);
    }

    @Test
    void throwsNotFoundWhenEventDoesNotExist() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.getImpactScore(999));
    }

    @Test
    void scopesMetricQueryToRequestedEventAndUsesEngagementAndRecurrenceSources() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(metrics(10, 5, 2, 4.0, 2, 1)));

        service.getImpactScore(24);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("WHERE e.id = ?"));
        assertTrue(sql.getValue().contains("FROM interactions i"));
        assertTrue(sql.getValue().contains("FROM product_interactions pi"));
        assertTrue(sql.getValue().contains("FROM surveys s"));
        assertTrue(sql.getValue().contains("FROM conversions c"));
        assertTrue(sql.getValue().contains("prior_event.event_date < e.event_date"));
    }

    private Map<String, Object> metrics(
            long registered,
            long attendees,
            long engaged,
            Double averageRating,
            long converted,
            long recurrent) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("registered", registered);
        metrics.put("attendees", attendees);
        metrics.put("engaged_participants", engaged);
        metrics.put("average_rating", averageRating);
        metrics.put("converted_participants", converted);
        metrics.put("recurrent_participants", recurrent);
        return metrics;
    }
}
