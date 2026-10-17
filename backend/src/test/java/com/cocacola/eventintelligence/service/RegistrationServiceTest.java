package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @InjectMocks
    private RegistrationService registrationService;

    private Event event;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(10L);
    }

    @Test
    void shouldCreateParticipantAndRegistrationForNewEmail() {
        stubExistingEvent();
        stubSuccessfulRegistration();
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.empty());
        when(participantRepository.save(any())).thenAnswer(invocation -> {
            Participant participant = invocation.getArgument(0);
            participant.setId(20L);
            return participant;
        });

        RegistrationResponse response = registrationService.register(request("ana@example.com"));

        assertEquals(10L, response.eventId());
        assertEquals(20L, response.participantId());
        assertTrue(isUuid(response.qrToken()));
        verify(participantRepository).save(any(Participant.class));
        verify(registrationRepository).saveAndFlush(any(Registration.class));
    }

    @Test
    void shouldReuseParticipantFoundByEmail() {
        stubExistingEvent();
        stubSuccessfulRegistration();
        Participant participant = participant(20L);
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(participant));

        RegistrationResponse response = registrationService.register(request("ANA@example.com"));

        assertEquals(20L, response.participantId());
        verify(participantRepository, never()).save(any(Participant.class));
    }

    @Test
    void shouldRejectDuplicateRegistrationForSameEvent() {
        stubExistingEvent();
        Participant participant = participant(20L);
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(participant));
        when(registrationRepository.existsByEventIdAndParticipantId(10L, 20L)).thenReturn(true);

        DuplicateRegistrationException exception = assertThrows(DuplicateRegistrationException.class,
                () -> registrationService.register(request("ana@example.com")));

        assertEquals("El participante 20 ya está registrado en el evento 10", exception.getMessage());
        verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
    }

    @Test
    void shouldRejectUnknownEvent() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());
        RegistrationRequest request = new RegistrationRequest(99L, "Ana", "Perez", "ana@example.com", null);

        assertThrows(ResourceNotFoundException.class, () -> registrationService.register(request));
        verify(participantRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void shouldGenerateDifferentUniqueTokens() {
        stubExistingEvent();
        stubSuccessfulRegistration();
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.empty());
        when(participantRepository.findByEmailIgnoreCase("luis@example.com")).thenReturn(Optional.empty());
        when(participantRepository.save(any())).thenAnswer(invocation -> {
            Participant participant = invocation.getArgument(0);
            participant.setId(participant.getEmail().startsWith("ana") ? 20L : 21L);
            return participant;
        });

        RegistrationResponse first = registrationService.register(request("ana@example.com"));
        RegistrationResponse second = registrationService.register(request("luis@example.com"));

        assertNotEquals(first.qrToken(), second.qrToken());
        assertTrue(isUuid(first.qrToken()));
        assertTrue(isUuid(second.qrToken()));
    }

    // ---------------------------------------------------------------------
    // Tarjeta P2-05: pruebas nuevas P2-05-C01 .. P2-05-C06 (patrón AAA)
    // ---------------------------------------------------------------------

    /** P2-05-C01: normaliza espacios en nombre, apellido y correo al crear participante. */
    @Test
    void shouldTrimNamesAndNormalizeEmailWhenCreatingParticipant() {
        stubExistingEvent();
        stubSuccessfulRegistration();
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.empty());
        stubParticipantSaveAssigningId(20L);
        RegistrationRequest request = new RegistrationRequest(10L, "  Ana  ", "\tPerez  ", "  ANA@Example.COM  ", null);

        RegistrationResponse response = registrationService.register(request);

        Participant saved = captureSavedParticipant();
        assertEquals("Ana", saved.getFirstName());
        assertEquals("Perez", saved.getLastName());
        assertEquals("ana@example.com", saved.getEmail());
        assertEquals("Ana Perez", response.participantName());
    }

    /** P2-05-C02: teléfono y campos opcionales en blanco se guardan como null. */
    @Test
    void shouldConvertBlankPhoneAndOptionalFieldsToNull() {
        stubExistingEvent();
        stubSuccessfulRegistration();
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.empty());
        stubParticipantSaveAssigningId(20L);
        RegistrationRequest request = new RegistrationRequest(
                10L, "Ana", "Perez", "ana@example.com", "   ", "", "  ", "\t", false);

        registrationService.register(request);

        Participant saved = captureSavedParticipant();
        assertNull(saved.getPhone());
        assertNull(saved.getCity());
        assertNull(saved.getAgeRange());
        assertNull(saved.getProductPreference());
    }

    /** P2-05-C03: consentimiento afirmativo se guarda y registra consentAt. */
    @Test
    void shouldStoreAffirmativeConsentAndRecordConsentTimestamp() {
        stubExistingEvent();
        stubSuccessfulRegistration();
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.empty());
        stubParticipantSaveAssigningId(20L);
        RegistrationRequest request = new RegistrationRequest(
                10L, "Ana", "Perez", "ana@example.com", null, null, null, null, true);
        OffsetDateTime before = OffsetDateTime.now();

        registrationService.register(request);

        OffsetDateTime after = OffsetDateTime.now();
        Participant saved = captureSavedParticipant();
        assertTrue(saved.getCommunicationConsent());
        assertNotNull(saved.getConsentAt());
        assertFalse(saved.getConsentAt().isBefore(before));
        assertFalse(saved.getConsentAt().isAfter(after));
    }

    /** P2-05-C04: conflicto uq_participants_phone se traduce a BusinessRuleException. */
    @Test
    void shouldTranslatePhoneUniqueConflictToBusinessRuleException() {
        stubExistingEvent();
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.empty());
        when(participantRepository.save(any(Participant.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_participants_phone\"")));
        RegistrationRequest request = new RegistrationRequest(10L, "Ana", "Perez", "ana@example.com", "+591 70000000");

        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                () -> registrationService.register(request));

        assertEquals("Ese teléfono ya está asociado a otra persona. Usa otro número o déjalo en blanco.",
                exception.getMessage());
        verifyNoInteractions(registrationRepository);
    }

    /** P2-05-C05: uk_registrations_event_participant anidada se traduce a DuplicateRegistrationException. */
    @Test
    void shouldTranslateNestedRegistrationUniqueConstraintToDuplicateRegistrationException() {
        stubExistingEvent();
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(participant(20L)));
        when(registrationRepository.existsByEventIdAndParticipantId(10L, 20L)).thenReturn(false);
        when(registrationRepository.saveAndFlush(any(Registration.class)))
                .thenThrow(integrityViolationCausedByConstraint("uk_registrations_event_participant"));

        DuplicateRegistrationException exception = assertThrows(DuplicateRegistrationException.class,
                () -> registrationService.register(request("ana@example.com")));

        assertEquals("El participante 20 ya está registrado en el evento 10", exception.getMessage());
        verify(registrationRepository).saveAndFlush(any(Registration.class));
    }

    /** P2-05-C06: un error de integridad ajeno a la inscripción duplicada se propaga sin traducir. */
    @Test
    void shouldPropagateIntegrityErrorUnrelatedToDuplicateRegistration() {
        stubExistingEvent();
        when(participantRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(participant(20L)));
        when(registrationRepository.existsByEventIdAndParticipantId(10L, 20L)).thenReturn(false);
        DataIntegrityViolationException otherViolation =
                integrityViolationCausedByConstraint("fk_registrations_participant");
        when(registrationRepository.saveAndFlush(any(Registration.class))).thenThrow(otherViolation);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> registrationService.register(request("ana@example.com")));

        assertSame(otherViolation, thrown);
    }

    private void stubParticipantSaveAssigningId(Long id) {
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> {
            Participant participant = invocation.getArgument(0);
            participant.setId(id);
            return participant;
        });
    }

    private Participant captureSavedParticipant() {
        ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(captor.capture());
        return captor.getValue();
    }

    private DataIntegrityViolationException integrityViolationCausedByConstraint(String constraintName) {
        org.hibernate.exception.ConstraintViolationException hibernateViolation =
                new org.hibernate.exception.ConstraintViolationException(
                        "could not execute statement", new SQLException("violación de restricción"), constraintName);
        return new DataIntegrityViolationException("error de integridad", new RuntimeException("envoltorio", hibernateViolation));
    }

    private void stubExistingEvent() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
    }

    private void stubSuccessfulRegistration() {
        when(registrationRepository.existsByEventIdAndParticipantId(any(), any())).thenReturn(false);
        when(registrationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Registration registration = invocation.getArgument(0);
            registration.setId(30L);
            return registration;
        });
    }

    private RegistrationRequest request(String email) {
        return new RegistrationRequest(10L, "Ana", "Perez", email, null);
    }

    private Participant participant(Long id) {
        Participant participant = new Participant();
        participant.setId(id);
        participant.setEmail("ana@example.com");
        return participant;
    }

    private boolean isUuid(String value) {
        try {
            return UUID.fromString(value).version() == 4;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}