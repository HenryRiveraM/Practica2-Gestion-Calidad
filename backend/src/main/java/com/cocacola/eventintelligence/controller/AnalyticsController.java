package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.service.AnalyticsOverviewService;
import com.cocacola.eventintelligence.service.EventImpactScoreService;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Validated
public class AnalyticsController {
    @org.springframework.beans.factory.annotation.Autowired
    protected com.cocacola.eventintelligence.security.AccessPolicy access;

    private final JdbcTemplate jdbcTemplate;
    private final AnalyticsOverviewService overviewService;
    private final EventImpactScoreService impactScoreService;

    public AnalyticsController(
            JdbcTemplate jdbcTemplate,
            AnalyticsOverviewService overviewService,
            EventImpactScoreService impactScoreService) {
        this.jdbcTemplate = jdbcTemplate;
        this.overviewService = overviewService;
        this.impactScoreService = impactScoreService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(
            @RequestParam(required = false) @Positive Long eventId,
            @RequestParam(required = false) @Positive Long campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la fecha final");
        }
        if (eventId != null) access.requireEvent(eventId);
        return overviewService.getScopedOverview(eventId, campaignId, startDate, endDate, access.eventScope());
    }

    @GetMapping("/events")
    public List<Map<String, Object>> eventPerformance() {
        return jdbcTemplate.queryForList(
                "SELECT event_id, event_name, event_date, campaign_name, registered, attended, attendance_rate_pct, " +
                "participation_rate_pct, conversion_rate_pct, avg_overall_satisfaction, nps, cost_per_attendee " +
                "FROM vw_event_performance" + scopeSql() + " ORDER BY event_date");
    }

    @GetMapping("/events/{eventId}")
    public Map<String, Object> eventPerformanceById(@PathVariable Long eventId) {
        access.requireEvent(eventId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM vw_event_performance WHERE event_id = ?", eventId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Evento", eventId);
        }
        return rows.getFirst();
    }

    @GetMapping("/events/{eventId}/impact-score")
    public Map<String, Object> eventImpactScore(@PathVariable @Positive Long eventId) {
        access.requireEvent(eventId);
        return impactScoreService.getImpactScore(eventId);
    }

    @GetMapping("/campaigns")
    public List<Map<String, Object>> campaignPerformance() {
        return jdbcTemplate.queryForList(
                "SELECT campaign_id, campaign_name, events_count, registered, attended, total_conversions, conversion_rate_pct, unique_attendees, avg_satisfaction, nps " +
                "FROM vw_campaign_performance ORDER BY campaign_id");
    }

    @GetMapping("/activities")
    public List<Map<String, Object>> activityAnalysis() {
        return jdbcTemplate.queryForList(
                "SELECT event_name, activity_name, type, unique_participants, total_interactions, participation_rate_pct, popularity_rank " +
                "FROM vw_activity_analysis" + scopeSql() + " ORDER BY event_id, popularity_rank");
    }

    @GetMapping("/products")
    public List<Map<String, Object>> productAnalysis() {
        return jdbcTemplate.queryForList(
                "SELECT event_name, product_name, category, total_interactions, total_tried, avg_rating, intent_low, intent_medium, intent_high, avg_intent_score " +
                "FROM vw_product_analysis" + scopeSql() + " ORDER BY event_id, product_name");
    }

    @GetMapping("/participants")
    public List<Map<String, Object>> participantAnalysis() {
        return jdbcTemplate.queryForList(
                "SELECT participant_id, full_name, city, age_range, product_preference, communication_consent, registration_source, events_attended, loyalty_status " +
                "FROM vw_participant_analysis" + (access.role() == com.cocacola.eventintelligence.security.Role.MARKETING ? " WHERE communication_consent=TRUE" : "") + " ORDER BY participant_id");
    }

    @GetMapping("/loyalty")
    public List<Map<String, Object>> loyaltySummary() {
        return jdbcTemplate.queryForList(
                "SELECT loyalty_status, COUNT(*) AS participants FROM vw_participant_analysis GROUP BY loyalty_status ORDER BY loyalty_status");
    }
    private String scopeSql() {
        var scope=access.eventScope();
        if(scope==null) return "";
        if(scope.isEmpty()) return " WHERE 1=0";
        return " WHERE event_id IN (" + scope.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + ")";
    }
}
