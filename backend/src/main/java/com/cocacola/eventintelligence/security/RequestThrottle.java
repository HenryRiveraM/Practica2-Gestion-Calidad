package com.cocacola.eventintelligence.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class RequestThrottle {
    private record Window(long start, int count) {}
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    public void check(String key, int limit) {
        long now = Instant.now().getEpochSecond();
        if (attempts.size() > 10000) attempts.entrySet().removeIf(entry -> now - entry.getValue().start() > 600);
        if (attempts.size() > 20000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Intenta nuevamente en unos minutos.");
        var window = attempts.compute(key, (k, old) -> old == null || now - old.start() > 600
                ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (window.count() > limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Intenta nuevamente en unos minutos.");
    }
}
