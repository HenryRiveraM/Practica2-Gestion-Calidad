package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.service.EventReportService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportController {
    @org.springframework.beans.factory.annotation.Autowired
    protected com.cocacola.eventintelligence.security.AccessPolicy access;

    private final EventReportService reportService;

    public ReportController(EventReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<byte[]> eventReport(@PathVariable @Positive Long eventId) {
        access.requireReports();
        access.requireEvent(eventId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"event-" + eventId + "-report.csv\"")
                .body(reportService.generateEventCsv(eventId));
    }
}
