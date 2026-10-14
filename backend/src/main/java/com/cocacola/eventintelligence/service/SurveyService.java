package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.dto.SurveyRequest;
import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.entity.Participant;
import com.cocacola.eventintelligence.entity.Survey;
import com.cocacola.eventintelligence.exception.DuplicateSurveyException;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import com.cocacola.eventintelligence.repository.EventRepository;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import com.cocacola.eventintelligence.repository.SurveyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurveyService extends AbstractCrudService<Survey> {
    private final AttendeeGuard attendeeGuard;
    private final SurveyRepository surveyRepository;
    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;

    public SurveyService(
            SurveyRepository repository,
            AttendeeGuard attendeeGuard,
            EventRepository eventRepository,
            ParticipantRepository participantRepository) {
        super(repository, "Encuesta");
        this.attendeeGuard = attendeeGuard;
        this.surveyRepository = repository;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public Survey submit(SurveyRequest request) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Evento", request.eventId()));
        Participant participant = participantRepository.findById(request.participantId())
                .orElseThrow(() -> new ResourceNotFoundException("Participante", request.participantId()));

        if (surveyRepository.existsByEventIdAndParticipantId(event.getId(), participant.getId())) {
            throw new DuplicateSurveyException(event.getId(), participant.getId());
        }

        attendeeGuard.requireCheckedIn(event.getId(), participant.getId());

        Survey survey = new Survey();
        survey.setEventId(event.getId());
        survey.setParticipantId(participant.getId());
        survey.setOrganizationScore(request.rating());
        survey.setServiceScore(request.rating());
        survey.setExperienceScore(request.rating());
        survey.setProductScore(request.rating());
        survey.setOverallSatisfaction(request.rating());
        survey.setNpsScore(request.nps());
        survey.setComment(request.comment() == null ? null : request.comment().trim());

        try {
            return surveyRepository.saveAndFlush(survey);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateSurveyConstraint(exception)) {
                throw new DuplicateSurveyException(event.getId(), participant.getId());
            }
            throw exception;
        }
    }

    @Override
    protected void validate(Survey entity) {
        attendeeGuard.requireCheckedIn(entity.getEventId(), entity.getParticipantId());
    }

    private boolean isDuplicateSurveyConstraint(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_surveys_event_participant".equals(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}