package com.cocacola.eventintelligence.service;

import java.util.List;

public record InsightReport(
        String summary,
        List<String> insights,
        List<String> anomalies,
        List<String> recommendations) {
}
