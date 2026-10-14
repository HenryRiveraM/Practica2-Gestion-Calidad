package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.dto.RegistrationRequest;
import com.cocacola.eventintelligence.dto.RegistrationResponse;
import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.entity.Participant;
import com.cocacola.eventintelligence.entity.Registration;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.exception.DuplicateRegistrationException;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import com.cocacola.eventintelligence.repository.EventRepository;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import com.cocacola.eventintelligence.repository.RegistrationRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final RegistrationRepository registrationRepository;

    public RegistrationService(
            EventRepository eventRepository,
            ParticipantRepository participantRepository,
            RegistrationRepository registrationRepository) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Evento", request.eventId()));

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Participant participant = participantRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createParticipant(request, email));

        if (registrationRepository.existsByEventIdAndParticipantId(event.getId(), participant.getId())) {
            throw new DuplicateRegistrationException(event.getId(), participant.getId());
        }

        Registration registration = new Registration();
        registration.setEventId(event.getId());
        registration.setParticipantId(participant.getId());
        registration.setToken(UUID.randomUUID().toString());

        try {
            Registration saved = registrationRepository.saveAndFlush(registration);
            return new RegistrationResponse(
                    saved.getId(),
                    saved.getEventId(),
                    event.getName(),
                    saved.getParticipantId(),
                    participant.getFirstName() + " " + participant.getLastName(),
                    saved.getToken());
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateRegistrationConstraint(exception)) {
                throw new DuplicateRegistrationException(event.getId(), participant.getId());
            }
            throw exception;
        }
    }

    private boolean isDuplicateRegistrationConstraint(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_registrations_event_participant".equals(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private Participant createParticipant(RegistrationRequest request, String email) {
        Participant participant = new Participant();
        participant.setFirstName(request.firstName().trim());
        participant.setLastName(request.lastName().trim());
        participant.setEmail(email);
        // La base tiene un índice único sobre phone: un teléfono vacío ("") debe guardarse como NULL.
        participant.setPhone(blankToNull(request.phone()));
        participant.setCity(blankToNull(request.city()));
        participant.setAgeRange(blankToNull(request.ageRange()));
        participant.setProductPreference(blankToNull(request.productPreference()));
        participant.setCommunicationConsent(Boolean.TRUE.equals(request.communicationConsent()));
        if (Boolean.TRUE.equals(request.communicationConsent())) participant.setConsentAt(java.time.OffsetDateTime.now());
        try {
            return participantRepository.save(participant);
        } catch (DataIntegrityViolationException exception) {
            String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
            if (cause.contains("uq_participants_phone")) {
                throw new BusinessRuleException("Ese teléfono ya está asociado a otra persona. Usa otro número o déjalo en blanco.");
            }
            throw exception;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}