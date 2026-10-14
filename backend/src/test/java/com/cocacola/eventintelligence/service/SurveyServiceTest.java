package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.dto.SurveyRequest;
import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.entity.Participant;
import com.cocacola.eventintelligence.entity.Survey;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.exception.DuplicateSurveyException;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import com.cocacola.eventintelligence.repository.EventRepository;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import com.cocacola.eventintelligence.repository.SurveyRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {
    @Mock private SurveyRepository surveyRepository;
    @Mock private AttendeeGuard attendeeGuard;
    @Mock private EventRepository eventRepository;
    @Mock private ParticipantRepository participantRepository;
    @InjectMocks private SurveyService service;

    private Event event;
    private Participant participant;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(10L);
        participant = new Participant();
        participant.setId(20L);
    }

    @Test
    @DisplayName("P2-06-C01: guarda identidad, satisfacción y NPS de una encuesta válida")
    void savesValidSurveyAndReturnsPersistedEntity() {
        existingEventAndParticipant();
        Survey persisted = new Survey();
        persisted.setId(30L);
        when(surveyRepository.saveAndFlush(any(Survey.class))).thenReturn(persisted);

        Survey result = service.submit(request("Excelente"));

        var captor = org.mockito.ArgumentCaptor.forClass(Survey.class);
        verify(surveyRepository).saveAndFlush(captor.capture());
        Survey submitted = captor.getValue();
        assertAll(
                () -> assertSame(persisted, result),
                () -> assertEquals(10L, submitted.getEventId()),
                () -> assertEquals(20L, submitted.getParticipantId()),
                () -> assertEquals((short) 4, submitted.getOrganizationScore()),
                () -> assertEquals((short) 4, submitted.getServiceScore()),
                () -> assertEquals((short) 4, submitted.getExperienceScore()),
                () -> assertEquals((short) 4, submitted.getProductScore()),
                () -> assertEquals((short) 4, submitted.getOverallSatisfaction()),
                () -> assertEquals((short) 9, submitted.getNpsScore()));
        var order = inOrder(attendeeGuard, surveyRepository);
        order.verify(attendeeGuard).requireCheckedIn(10L, 20L);
        order.verify(surveyRepository).saveAndFlush(any(Survey.class));
    }

    @Test
    @DisplayName("P2-06-C02: recorta el comentario")
    void trimsComment() {
        existingEventAndParticipant();
        when(surveyRepository.saveAndFlush(any(Survey.class))).thenAnswer(i -> i.getArgument(0));

        Survey result = service.submit(request("  Buena experiencia  "));

        assertEquals("Buena experiencia", result.getComment());
    }

    @Test
    @DisplayName("P2-06-C03: conserva el comentario ausente")
    void preservesNullComment() {
        existingEventAndParticipant();
        when(surveyRepository.saveAndFlush(any(Survey.class))).thenAnswer(i -> i.getArgument(0));

        Survey result = service.submit(request(null));

        assertNull(result.getComment());
    }

    @Test
    @DisplayName("P2-06-C04: rechaza un evento inexistente sin continuar")
    void rejectsMissingEvent() {
        when(eventRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.submit(request(null)));

        verifyNoInteractions(participantRepository, attendeeGuard, surveyRepository);
    }

    @Test
    @DisplayName("P2-06-C05: rechaza un participante inexistente sin guardar")
    void rejectsMissingParticipant() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(participantRepository.findById(20L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.submit(request(null)));

        verifyNoInteractions(attendeeGuard, surveyRepository);
    }

    @Test
    @DisplayName("P2-06-C06: rechaza una encuesta duplicada antes de validar asistencia")
    void rejectsExistingSurvey() {
        existingEventAndParticipant();
        when(surveyRepository.existsByEventIdAndParticipantId(10L, 20L)).thenReturn(true);

        assertThrows(DuplicateSurveyException.class, () -> service.submit(request(null)));

        verifyNoInteractions(attendeeGuard);
        verify(surveyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("P2-06-C07: rechaza la falta de check-in sin guardar")
    void rejectsParticipantWithoutCheckIn() {
        existingEventAndParticipant();
        BusinessRuleException denied = new BusinessRuleException("Debe realizar check-in");
        doThrow(denied).when(attendeeGuard).requireCheckedIn(10L, 20L);

        assertSame(denied, assertThrows(BusinessRuleException.class,
                () -> service.submit(request(null))));

        verify(surveyRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "P2-06-C08: restricción {0}")
    @ValueSource(strings = {"uk_surveys_event_participant", "another_constraint"})
    void distinguishesDuplicateConstraintFromOtherIntegrityErrors(String constraint) {
        existingEventAndParticipant();
        var cause = new ConstraintViolationException("Conflicto", new SQLException("integridad"), constraint);
        var failure = new DataIntegrityViolationException("No se pudo guardar",
                new IllegalStateException("Causa anidada", cause));
        when(surveyRepository.saveAndFlush(any(Survey.class))).thenThrow(failure);

        if ("uk_surveys_event_participant".equals(constraint)) {
            assertThrows(DuplicateSurveyException.class, () -> service.submit(request(null)));
        } else {
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.submit(request(null))));
        }
        verify(surveyRepository).saveAndFlush(any(Survey.class));
    }

    private SurveyRequest request(String comment) {
        return new SurveyRequest(10L, 20L, (short) 4, (short) 9, comment);
    }

    private void existingEventAndParticipant() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(participantRepository.findById(20L)).thenReturn(Optional.of(participant));
    }
}
