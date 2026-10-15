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

    // ---------------------------------------------------------------------
    // Tarjeta P2-07: pruebas nuevas P2-07-C01 .. P2-07-C06 (patrón AAA)
    // ---------------------------------------------------------------------

    /** P2-07-C01: asistencia cero con inscripciones y sin asistentes; las métricas que dependen de asistentes quedan ausentes. */
    @Test
    void shouldReportZeroAttendanceAndOmitAttendeeBasedMetricsWhenNobodyCheckedIn() {
        // Arrange
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(metrics(10, 0, 0, null, 0, 2)));

        // Act
        Map<String, Object> response = service.getImpactScore(12);

        // Assert
        Map<?, ?> metrics = (Map<?, ?>) response.get("metrics");
        assertEquals(0.0, metrics.get("attendance"));
        assertNull(metrics.get("engagement"));
        assertNull(metrics.get("conversion"));
        assertEquals(20.0, metrics.get("recurrence"));
        assertEquals(List.of("engagement", "satisfaction", "conversion"), response.get("missingMetrics"));
        assertEquals("PARTIAL_DATA", response.get("status"));
        assertEquals(5.0, response.get("score"));
    }

    /** P2-07-C02: la satisfacción se calcula aunque falten las demás métricas. */
    @Test
    void shouldCalculateScoreFromSatisfactionAloneWhenOtherMetricsAreMissing() {
        // Arrange
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(metrics(0, 0, 0, 4.0, 0, 0)));

        // Act
        Map<String, Object> response = service.getImpactScore(12);

        // Assert
        Map<?, ?> metrics = (Map<?, ?>) response.get("metrics");
        assertEquals(80.0, metrics.get("satisfaction"));
        assertEquals(80.0, response.get("score"));
        assertEquals("PARTIAL_DATA", response.get("status"));
        assertEquals(List.of("attendance", "engagement", "conversion", "recurrence"), response.get("missingMetrics"));
        assertEquals(Map.of("satisfaction", 1.0), response.get("appliedWeights"));
    }

    /** P2-07-C03: un average_rating no numérico se trata como métrica ausente. */
    @Test
    void shouldTreatNonNumericAverageRatingAsMissingMetric() {
        // Arrange
        Map<String, Object> row = metrics(100, 80, 64, null, 40, 20);
        row.put("average_rating", "N/A");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        // Act
        Map<String, Object> response = service.getImpactScore(12);

        // Assert
        Map<?, ?> metrics = (Map<?, ?>) response.get("metrics");
        assertNull(metrics.get("satisfaction"));
        assertEquals(List.of("satisfaction"), response.get("missingMetrics"));
        assertEquals("PARTIAL_DATA", response.get("status"));
        assertEquals(65.0, response.get("score"));
    }

    /** P2-07-C04: un contador no numérico se trata como cero (contrato actual); la métrica queda presente con 0.0. */
    @Test
    void shouldTreatNonNumericCounterAsZeroAndKeepMetricPresent() {
        // Arrange
        Map<String, Object> row = metrics(100, 80, 64, 4.5, 0, 20);
        row.put("converted_participants", "abc");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        // Act
        Map<String, Object> response = service.getImpactScore(12);

        // Assert
        Map<?, ?> metrics = (Map<?, ?>) response.get("metrics");
        assertEquals(0.0, metrics.get("conversion"));
        assertEquals(List.of(), response.get("missingMetrics"));
        assertEquals("CALCULATED", response.get("status"));
        assertEquals(60.0, response.get("score"));
    }

    /** P2-07-C05: un porcentaje no entero se redondea a un decimal. */
    @Test
    void shouldRoundNonIntegerPercentageToOneDecimal() {
        // Arrange: 4 / 6 * 100 = 66.666...
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(metrics(6, 4, 0, null, 0, 0)));

        // Act
        Map<String, Object> response = service.getImpactScore(12);

        // Assert
        Map<?, ?> metrics = (Map<?, ?>) response.get("metrics");
        assertEquals(66.7, metrics.get("attendance"));
    }

    /** P2-07-C06: el puntaje ponderado con métricas fraccionarias se redondea a un decimal. */
    @Test
    void shouldRoundWeightedScoreToOneDecimalWithFractionalMetrics() {
        // Arrange: asistencia 33.3, interacción 100.0, satisfacción 66.6, conversión 0.0, recurrencia 33.3
        // Suma ponderada = 46.64 -> debe informarse como 46.6
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(metrics(3, 1, 1, 3.33, 0, 1)));

        // Act
        Map<String, Object> response = service.getImpactScore(12);

        // Assert
        Map<?, ?> metrics = (Map<?, ?>) response.get("metrics");
        assertEquals(33.3, metrics.get("attendance"));
        assertEquals(100.0, metrics.get("engagement"));
        assertEquals(0.0, metrics.get("conversion"));
        assertEquals(33.3, metrics.get("recurrence"));
        assertEquals("CALCULATED", response.get("status"));
        assertEquals(46.6, response.get("score"));
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