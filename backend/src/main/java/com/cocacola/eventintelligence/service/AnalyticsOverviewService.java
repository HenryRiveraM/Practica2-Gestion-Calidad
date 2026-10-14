package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsOverviewService {
    private static final String FILTERED_EVENTS = """
            WITH filtered_events AS (
                SELECT e.id, e.event_date
                FROM events e
                WHERE 1 = 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsOverviewService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOverview(
            Long eventId,
            Long campaignId,
            LocalDate startDate,
            LocalDate endDate) {
        return getScopedOverview(eventId, campaignId, startDate, endDate, null);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> getScopedOverview(Long eventId, Long campaignId, LocalDate startDate, LocalDate endDate, java.util.Set<Long> scope) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringBuilder eventFilter = new StringBuilder();
        addFilter(eventFilter, parameters, eventId, campaignId, startDate, endDate);
        if (scope != null) {
            eventFilter.append(scope.isEmpty() ? " AND 1=0" : " AND e.id IN (:allowedEventIds)");
            if (!scope.isEmpty()) parameters.addValue("allowedEventIds", scope);
        }

        String aggregationSql = FILTERED_EVENTS + eventFilter + """
                    ),
                    scoped_registrations AS (
                        SELECT r.participant_id, e.id AS event_id, e.event_date
                        FROM registrations r
                        JOIN filtered_events e ON e.id = r.event_id
                        UNION
                        SELECT a.participant_id, e.id AS event_id, e.event_date
                        FROM attendances a
                        JOIN filtered_events e ON e.id = a.event_id
                        WHERE a.status <> 'CANCELLED'
                    ),
                    first_scoped_registration AS (
                        SELECT participant_id, MIN(event_date) AS first_event_date
                        FROM scoped_registrations
                        GROUP BY participant_id
                    ),
                    participant_history AS (
                        SELECT f.participant_id,
                            EXISTS (
                                SELECT 1
                                FROM registrations previous_registration
                                JOIN events previous_event ON previous_event.id = previous_registration.event_id
                                WHERE previous_registration.participant_id = f.participant_id
                                  AND previous_event.event_date < f.first_event_date
                            ) OR EXISTS (
                                SELECT 1
                                FROM attendances previous_attendance
                                JOIN events previous_event ON previous_event.id = previous_attendance.event_id
                                WHERE previous_attendance.participant_id = f.participant_id
                                  AND previous_attendance.status <> 'CANCELLED'
                                  AND previous_event.event_date < f.first_event_date
                            ) AS recurrent
                        FROM first_scoped_registration f
                    ),
                    attendance_totals AS (
                        SELECT COUNT(*) AS attendees
                        FROM attendances a
                        JOIN filtered_events e ON e.id = a.event_id
                        WHERE a.status = 'CHECKED_IN'
                    ),
                    registration_totals AS (
                        SELECT COUNT(*) AS registered FROM scoped_registrations
                    ),
                    conversion_totals AS (
                        SELECT COUNT(*) AS conversions
                        FROM conversions c
                        JOIN filtered_events e ON e.id = c.event_id
                    ),
                    survey_totals AS (
                        SELECT COUNT(*) AS survey_count,
                            ROUND(AVG(s.overall_satisfaction)::numeric, 2) AS average_rating,
                            CASE WHEN COUNT(*) = 0 THEN NULL
                                ELSE ROUND(100.0 * (
                                    COUNT(*) FILTER (WHERE s.nps_score >= 9)
                                    - COUNT(*) FILTER (WHERE s.nps_score <= 6)
                                ) / COUNT(*), 1)
                            END AS nps
                        FROM surveys s
                        JOIN filtered_events e ON e.id = s.event_id
                    )
                    SELECT r.registered AS registered,
                        a.attendees AS attendees,
                        CASE WHEN r.registered = 0 THEN NULL
                            ELSE ROUND(100.0 * a.attendees / r.registered, 1)
                        END AS attendance_rate,
                        c.conversions AS conversions,
                        CASE WHEN a.attendees = 0 THEN NULL
                            ELSE ROUND(100.0 * c.conversions / a.attendees, 1)
                        END AS conversion_rate,
                        (SELECT COUNT(*) FROM participant_history WHERE recurrent = FALSE) AS new_participants,
                        (SELECT COUNT(*) FROM participant_history WHERE recurrent = TRUE) AS recurrent_participants,
                        s.nps AS nps,
                        s.average_rating AS average_rating
                    FROM registration_totals r
                    CROSS JOIN attendance_totals a
                    CROSS JOIN conversion_totals c
                    CROSS JOIN survey_totals s
                """;

        Map<String, Object> result = new LinkedHashMap<>(jdbcTemplate.queryForMap(aggregationSql, parameters));
        String peakHoursSql = FILTERED_EVENTS + eventFilter + """
                    )
                    SELECT EXTRACT(HOUR FROM a.check_in_at AT TIME ZONE 'America/La_Paz')::integer AS hour,
                        COUNT(*) AS attendees
                    FROM attendances a
                    JOIN filtered_events e ON e.id = a.event_id
                    WHERE a.status = 'CHECKED_IN'
                      AND a.check_in_at IS NOT NULL
                    GROUP BY hour
                    ORDER BY attendees DESC, hour
                """;
        List<Map<String, Object>> peakHours = jdbcTemplate.queryForList(peakHoursSql, parameters);

        result.put("peakAttendanceHours", peakHours.stream()
                .map(row -> Map.of(
                        "hour", ((Number) row.get("hour")).intValue(),
                        "attendees", ((Number) row.get("attendees")).longValue()))
                .toList());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEventInsightData(long eventId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("eventId", eventId);
        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
                SELECT e.id, e.name, e.event_date, e.location, c.name AS campaign_name
                FROM events e
                LEFT JOIN campaigns c ON c.id = e.campaign_id
                WHERE e.id = :eventId
                """, parameters);
        if (events.isEmpty()) {
            throw new ResourceNotFoundException("Evento", eventId);
        }

        Map<String, Object> metrics = new LinkedHashMap<>(getOverview(eventId, null, null, null));
        Map<String, Object> engagement = jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT engaged.participant_id) AS engaged_participants,
                    CASE WHEN attendance.attendees = 0 THEN NULL
                        ELSE ROUND(100.0 * COUNT(DISTINCT engaged.participant_id) / attendance.attendees, 1)
                    END AS engagement_rate
                FROM events e
                LEFT JOIN (
                    SELECT i.event_id, i.participant_id
                    FROM interactions i
                    WHERE i.event_id = :eventId
                    UNION
                    SELECT pi.event_id, pi.participant_id
                    FROM product_interactions pi
                    WHERE pi.event_id = :eventId
                ) engaged ON engaged.event_id = e.id
                CROSS JOIN (
                    SELECT COUNT(DISTINCT a.participant_id) AS attendees
                    FROM attendances a
                    WHERE a.event_id = :eventId AND a.status = 'CHECKED_IN'
                ) attendance
                WHERE e.id = :eventId
                GROUP BY attendance.attendees
                """, parameters);
        metrics.putAll(engagement);

        List<Map<String, Object>> activities = jdbcTemplate.queryForList("""
                SELECT a.type, COUNT(*) AS activity_count,
                    SUM(a.quantity) AS total_quantity,
                    COUNT(DISTINCT a.participant_id) AS participants
                FROM activities a
                WHERE a.event_id = :eventId
                GROUP BY a.type
                ORDER BY activity_count DESC, a.type
                """, parameters);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", events.getFirst());
        result.put("metrics", metrics);
        result.put("activities", activities);
        return result;
    }

    private void addFilter(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            Long eventId,
            Long campaignId,
            LocalDate startDate,
            LocalDate endDate) {
        if (eventId != null) {
            sql.append(" AND e.id = :eventId");
            parameters.addValue("eventId", eventId);
        }
        if (campaignId != null) {
            sql.append(" AND e.campaign_id = :campaignId");
            parameters.addValue("campaignId", campaignId);
        }
        if (startDate != null) {
            sql.append(" AND e.event_date >= :startDate");
            parameters.addValue("startDate", Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append(" AND e.event_date <= :endDate");
            parameters.addValue("endDate", Date.valueOf(endDate));
        }
    }
}