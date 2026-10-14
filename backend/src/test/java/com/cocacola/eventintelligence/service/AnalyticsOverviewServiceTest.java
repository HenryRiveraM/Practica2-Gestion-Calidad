package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class AnalyticsOverviewServiceTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private AnalyticsOverviewService service;

    @BeforeEach
    void setUp() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("hour", 14, "attendees", 6L)));
    }

    @Test
    void shouldReturnRealOverviewMetricsWithoutFilters() {
        stubOverviewMetrics();

        Map<String, Object> response = service.getOverview(null, null, null, null);

        assertEquals(12L, response.get("registered"));
        assertEquals(9L, response.get("attendees"));
        assertEquals(75.0, response.get("attendance_rate"));
        assertEquals(3L, response.get("conversions"));
        assertEquals(33.3, response.get("conversion_rate"));
        assertEquals(4.5, response.get("average_rating"));
        assertEquals(List.of(Map.of("hour", 14, "attendees", 6L)), response.get("peakAttendanceHours"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForMap(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("FROM registrations r"));
        assertTrue(((MapSqlParameterSource) parameters.getValue()).getValues().isEmpty());
    }

    @Test
    void shouldApplyEventFilterInDatabaseQueries() {
        stubOverviewMetrics();
        service.getOverview(4L, null, null, null);

        ArgumentCaptor<String> aggregateSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> aggregateParams = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForMap(aggregateSql.capture(), aggregateParams.capture());
        assertTrue(aggregateSql.getValue().contains("e.id = :eventId"));
        assertEquals(4L, aggregateParams.getValue().getValue("eventId"));

        ArgumentCaptor<String> peakSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> peakParams = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForList(peakSql.capture(), peakParams.capture());
        assertTrue(peakSql.getValue().contains("e.id = :eventId"));
        assertEquals(4L, peakParams.getValue().getValue("eventId"));
    }

    @Test
    void shouldApplyInclusiveDateRangeInDatabaseQueries() {
        stubOverviewMetrics();
        service.getOverview(null, null, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForMap(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("e.event_date >= :startDate"));
        assertTrue(sql.getValue().contains("e.event_date <= :endDate"));
        assertEquals(Date.valueOf("2026-06-01"), parameters.getValue().getValue("startDate"));
        assertEquals(Date.valueOf("2026-06-30"), parameters.getValue().getValue("endDate"));
    }

    @Test
    void shouldClassifyUniqueParticipantsAndCalculateAttendanceAndConversions() {
        stubOverviewMetrics();
        service.getOverview(null, 3L, null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForMap(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("previous_registration.participant_id = f.participant_id"));
        assertTrue(sql.getValue().contains("previous_attendance.participant_id = f.participant_id"));
        assertTrue(sql.getValue().contains("WHERE recurrent = FALSE"));
        assertTrue(sql.getValue().contains("WHERE recurrent = TRUE"));
        assertTrue(sql.getValue().contains("a.status = 'CHECKED_IN'"));
        assertTrue(sql.getValue().contains("FROM conversions c"));
        assertTrue(sql.getValue().contains("AVG(s.overall_satisfaction)"));
        assertTrue(sql.getValue().contains("s.nps_score >= 9"));
        assertTrue(sql.getValue().contains("s.nps_score <= 6"));
        assertEquals(3L, parameters.getValue().getValue("campaignId"));
    }

    @Test
    void shouldLeaveRatesAndSurveyScoresUndefinedWhenThereIsNoData() {
        Map<String, Object> emptyMetrics = new HashMap<>();
        emptyMetrics.put("registered", 0L);
        emptyMetrics.put("attendees", 0L);
        emptyMetrics.put("attendance_rate", null);
        emptyMetrics.put("conversions", 0L);
        emptyMetrics.put("conversion_rate", null);
        emptyMetrics.put("new_participants", 0L);
        emptyMetrics.put("recurrent_participants", 0L);
        emptyMetrics.put("nps", null);
        emptyMetrics.put("average_rating", null);
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(emptyMetrics);

        Map<String, Object> response = service.getOverview(null, null, null, null);

        assertNull(response.get("attendance_rate"));
        assertNull(response.get("conversion_rate"));
        assertNull(response.get("nps"));
        assertNull(response.get("average_rating"));
    }

    private void stubOverviewMetrics() {
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(Map.of(
                "registered", 12L,
                "attendees", 9L,
                "attendance_rate", 75.0,
                "conversions", 3L,
                "conversion_rate", 33.3,
                "new_participants", 7L,
                "recurrent_participants", 5L,
                "nps", 40.0,
                "average_rating", 4.5));
    }
}
