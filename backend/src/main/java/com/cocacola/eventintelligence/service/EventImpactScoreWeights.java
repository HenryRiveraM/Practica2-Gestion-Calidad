package com.cocacola.eventintelligence.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EventImpactScoreWeights {
    public static final Map<String, Double> WEIGHTS;

    static {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("attendance", 0.30);
        weights.put("engagement", 0.20);
        weights.put("satisfaction", 0.20);
        weights.put("conversion", 0.20);
        weights.put("recurrence", 0.10);
        WEIGHTS = Collections.unmodifiableMap(weights);
    }

    private EventImpactScoreWeights() {
    }
}
