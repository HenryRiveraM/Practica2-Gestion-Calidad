package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.ProductInteraction;
import com.cocacola.eventintelligence.repository.ProductInteractionRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductInteractionService extends AbstractCrudService<ProductInteraction> {
    private final AttendeeGuard attendeeGuard;

    public ProductInteractionService(ProductInteractionRepository repository, AttendeeGuard attendeeGuard) {
        super(repository, "Interacción de producto");
        this.attendeeGuard = attendeeGuard;
    }

    @Override
    protected void validate(ProductInteraction entity) {
        attendeeGuard.requireCheckedIn(entity.getEventId(), entity.getParticipantId());
    }
}