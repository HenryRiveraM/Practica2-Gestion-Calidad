package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Activity;
import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.dto.ActivityRequest;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import com.cocacola.eventintelligence.repository.ActivityRepository;
import com.cocacola.eventintelligence.repository.EventRepository;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService extends AbstractCrudService<Activity> {
    private final ActivityRepository activityRepository;
    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;

    public ActivityService(
            ActivityRepository repository,
            EventRepository eventRepository,
            ParticipantRepository participantRepository) {
        super(repository, "Actividad");
        this.activityRepository = repository;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public Activity record(ActivityRequest request) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Evento", request.eventId()));
        if (request.participantId() != null) {
            participantRepository.findById(request.participantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Participante", request.participantId()));
        }

        Activity activity = new Activity();
        activity.setEventId(event.getId());
        activity.setParticipantId(request.participantId());
        activity.setName(request.type().trim());
        activity.setType(request.type().trim());
        activity.setQuantity(request.quantity());
        activity.setResult(request.result().trim());
        activity.setStartAt(request.occurredAt());
        activity.setStatus("COMPLETED");
        return activityRepository.save(activity);
    }
}