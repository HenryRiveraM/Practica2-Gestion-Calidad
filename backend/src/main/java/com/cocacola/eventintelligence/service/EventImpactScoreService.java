package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventImpactScoreService {
    private static final String EVENT_METRICS_SQL = """
            SELECT
                (SELECT COUNT(*) FROM (
                    SELECT r.participant_id FROM registrations r WHERE r.event_id = e.id
                    UNION
                    SELECT a.participant_id FROM attendances a
                    WHERE a.event_id = e.id AND a.status <> 'CANCELLED') enrolled) AS registered,
                (SELECT COUNT(DISTINCT a.participant_id)
                    FROM attendances a
                    WHERE a.event_id = e.id AND a.status = 'CHECKED_IN') AS attendees,
                (SELECT COUNT(DISTINCT engaged.participant_id)
                    FROM (
                        SELECT i.participant_id
                        FROM interactions i
                        WHERE i.event_id = e.id
                        UNION
                        SELECT pi.participant_id
                        FROM product_interactions pi
                        WHERE pi.event_id = e.id
                    ) engaged) AS engaged_participants,
                (SELECT AVG(s.overall_satisfaction)::double precision
                    FROM surveys s WHERE s.event_id = e.id) AS average_rating,
                (SELECT COUNT(DISTINCT c.participant_id)
                    FROM conversions c WHERE c.event_id = e.id) AS converted_participants,
                (SELECT COUNT(DISTINCT r.participant_id)
                    FROM registrations r
                    WHERE r.event_id = e.id
                      AND (
                        EXISTS (
                            SELECT 1
                            FROM registrations prior_registration
                            JOIN events prior_event ON prior_event.id = prior_registration.event_id
                            WHERE prior_registration.participant_id = r.participant_id
                              AND prior_event.event_date < e.event_date
                        )
                        OR EXISTS (
                            SELECT 1
                            FROM attendances prior_attendance
                            JOIN events prior_event ON prior_event.id = prior_attendance.event_id
                            WHERE prior_attendance.participant_id = r.participant_id
                              AND prior_attendance.status <> 'CANCELLED'
                              AND prior_event.event_date < e.event_date
                        )
                      )) AS recurrent_participants
            FROM events e
            WHERE e.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public EventImpactScoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getImpactScore(long eventId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(EVENT_METRICS_SQL, eventId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Evento", eventId);
        }

        Map<String, Object> row = rows.getFirst();
        double registered = number(row, "registered");
        double attendees = number(row, "attendees");
        double engaged = number(row, "engaged_participants");
        double recurrent = number(row, "recurrent_participants");
        Double averageRating = nullableNumber(row.get("average_rating"));
        double converted = number(row, "converted_participants");

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("attendance", percent(attendees, registered));
        metrics.put("engagement", percent(engaged, attendees));
        metrics.put("satisfaction", averageRating == null ? null : averageRating * 20.0);
        metrics.put("conversion", percent(converted, attendees));
        metrics.put("recurrence", percent(recurrent, registered));

        double weightedTotal = 0.0;
        double availableWeight = 0.0;
        List<String> missingMetrics = new ArrayList<>();
        Map<String, Double> appliedWeights = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : EventImpactScoreWeights.WEIGHTS.entrySet()) {
            Double value = metrics.get(entry.getKey());
            if (value == null) {
                missingMetrics.add(entry.getKey());
            } else {
                weightedTotal += value * entry.getValue();
                availableWeight += entry.getValue();
                appliedWeights.put(entry.getKey(), entry.getValue());
            }
        }
        if (availableWeight > 0) {
            for (String metric : appliedWeights.keySet()) {
                appliedWeights.put(metric, EventImpactScoreWeights.WEIGHTS.get(metric) / availableWeight);
            }
        }

        Double score = availableWeight == 0 ? null : round(weightedTotal / availableWeight);
        String status = score == null
                ? "INSUFFICIENT_DATA"
                : missingMetrics.isEmpty() ? "CALCULATED" : "PARTIAL_DATA";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId);
        result.put("score", score);
        result.put("status", status);
        result.put("metrics", metrics);
        result.put("weights", EventImpactScoreWeights.WEIGHTS);
        result.put("appliedWeights", appliedWeights);
        result.put("missingMetrics", missingMetrics);
        result.put("formula", "Weighted average of available 0-100 metrics; weights are renormalized over available metrics.");
        result.put("metricDefinitions", Map.of(
                "attendance", "Checked-in participants / registrations * 100",
                "engagement", "Unique participants with an interaction or product interaction / checked-in participants * 100",
                "satisfaction", "Average overall satisfaction / 5 * 100",
                "conversion", "Unique converted participants / checked-in participants * 100",
                "recurrence", "Registered participants with participation in an earlier event / registrations * 100"));
        return result;
    }

    private static double number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number numeric ? numeric.doubleValue() : 0.0;
    }

    private static Double nullableNumber(Object value) {
        return value instanceof Number numeric ? numeric.doubleValue() : null;
    }

    private static Double percent(double numerator, double denominator) {
        return denominator == 0 ? null : round(numerator * 100.0 / denominator);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}