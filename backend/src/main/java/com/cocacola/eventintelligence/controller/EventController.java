package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.service.EventService;
import com.cocacola.eventintelligence.service.InsightService;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@Validated
public class EventController extends BaseCrudController<Event> {
    private final InsightService insightService;

    public EventController(EventService service, InsightService insightService) {
        super(service);
        this.insightService = insightService;
    }

    @GetMapping("/{eventId}/insights")
    public Map<String, Object> insights(@PathVariable @Positive Long eventId) {
        access.requireReports();
        access.requireEvent(eventId);
        return insightService.getEventInsights(eventId);
    }
}
