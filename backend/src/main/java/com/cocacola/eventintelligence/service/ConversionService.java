package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Conversion;
import com.cocacola.eventintelligence.repository.ConversionRepository;
import org.springframework.stereotype.Service;

@Service
public class ConversionService extends AbstractCrudService<Conversion> {
    private final AttendeeGuard attendeeGuard;

    public ConversionService(ConversionRepository repository, AttendeeGuard attendeeGuard) {
        super(repository, "Conversión");
        this.attendeeGuard = attendeeGuard;
    }

    @Override
    protected void validate(Conversion entity) {
        attendeeGuard.requireCheckedIn(entity.getEventId(), entity.getParticipantId());
    }
}