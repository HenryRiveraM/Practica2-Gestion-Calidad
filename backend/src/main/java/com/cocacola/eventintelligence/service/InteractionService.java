package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Interaction;
import com.cocacola.eventintelligence.repository.InteractionRepository;
import org.springframework.stereotype.Service;

@Service
public class InteractionService extends AbstractCrudService<Interaction> {
    private final AttendeeGuard attendeeGuard;

    public InteractionService(InteractionRepository repository, AttendeeGuard attendeeGuard) {
        super(repository, "Interacción");
        this.attendeeGuard = attendeeGuard;
    }

    @Override
    protected void validate(Interaction entity) {
        attendeeGuard.requireCheckedIn(entity.getEventId(), entity.getParticipantId());
    }
}