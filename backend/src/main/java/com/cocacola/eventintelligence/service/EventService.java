package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.repository.EventRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService extends AbstractCrudService<Event> {
    private final EventRepository events;
    private final JdbcTemplate jdbc;

    public EventService(EventRepository repository, JdbcTemplate jdbc) {
        super(repository, "Evento");
        this.events = repository;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Event create(Event event) {
        Event saved = super.create(event);
        events.flush();
        synchronizePublication(saved, null);
        return saved;
    }

    @Override
    @Transactional
    public Event update(Long id, Event event) {
        // Serialize status and publication changes for the same event.
        jdbc.queryForList("SELECT id FROM events WHERE id=? FOR UPDATE", id);
        String previousStatus = findById(id).getStatus();
        Event saved = super.update(id, event);
        events.flush();
        synchronizePublication(saved, previousStatus);
        return saved;
    }

    private void synchronizePublication(Event event, String previousStatus) {
        if ("ACTIVE".equals(event.getStatus()) && !"ACTIVE".equals(previousStatus)) {
            jdbc.update("""
                INSERT INTO event_public_access(event_id,token,enabled) VALUES (?,?,TRUE)
                ON CONFLICT(event_id) DO UPDATE SET enabled=TRUE
                """, event.getId(), UUID.randomUUID().toString());
        } else if ("COMPLETED".equals(event.getStatus()) || "CANCELLED".equals(event.getStatus())) {
            jdbc.update("UPDATE event_public_access SET enabled=FALSE WHERE event_id=?", event.getId());
        }
    }
}
