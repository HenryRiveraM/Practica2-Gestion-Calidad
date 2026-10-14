package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.security.*;
import com.cocacola.eventintelligence.service.EventService;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/events")
public class EventAccessController {
    private final JdbcTemplate jdbc;
    private final AccessPolicy access;
    private final EventService events;
    private final AccountStore accounts;

    public EventAccessController(JdbcTemplate jdbc, AccessPolicy access, EventService events, AccountStore accounts) {
        this.jdbc=jdbc; this.access=access; this.events=events; this.accounts=accounts;
    }

    public record PublicAccess(boolean enabled, boolean reactivateEvent) {}

    @GetMapping("/{id}/public-access")
    public Map<String,Object> get(@PathVariable Long id) {
        access.requireAdmin();
        var event = events.findById(id);
        var rows = jdbc.queryForList("SELECT token,enabled FROM event_public_access WHERE event_id=?",id);
        Map<String,Object> result = new LinkedHashMap<>();
        if (!rows.isEmpty()) result.putAll(rows.getFirst());
        boolean eligible = Set.of("PLANNED", "ACTIVE").contains(event.getStatus());
        result.put("enabled", eligible && Boolean.TRUE.equals(result.get("enabled")));
        result.put("eventStatus", event.getStatus());
        return result;
    }

    @PutMapping("/{id}/public-access")
    @Transactional
    public Map<String,Object> set(@PathVariable Long id, @RequestBody PublicAccess body) {
        access.requireAdmin();
        jdbc.queryForList("SELECT id FROM events WHERE id=? FOR UPDATE",id);
        var event = events.findById(id);
        boolean closed = Set.of("COMPLETED", "CANCELLED").contains(event.getStatus());
        if (body.enabled() && closed) {
            if (!body.reactivateEvent()) throw new BusinessRuleException("El evento está cerrado. Usa Reactivar y publicar para abrir sus inscripciones.");
            event.setStatus("ACTIVE");
            events.update(id,event);
            accounts.audit(access.account().id(),"EVENT_REACTIVATED",id);
        }
        jdbc.update("""
            INSERT INTO event_public_access(event_id,token,enabled) VALUES (?,?,?)
            ON CONFLICT(event_id) DO UPDATE SET enabled=EXCLUDED.enabled
            """,id,UUID.randomUUID().toString(),body.enabled());
        accounts.audit(access.account().id(),body.enabled()?"PUBLIC_REGISTRATION_ENABLED":"PUBLIC_REGISTRATION_DISABLED",id);
        return get(id);
    }
}
