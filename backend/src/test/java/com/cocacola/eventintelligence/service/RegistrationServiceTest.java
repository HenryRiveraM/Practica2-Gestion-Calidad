package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.dto.RegistrationRequest;
import com.cocacola.eventintelligence.dto.RegistrationResponse;
import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.entity.Participant;
import com.cocacola.eventintelligence.entity.Registration;
import com.cocacola.eventintelligence.exception.DuplicateRegistrationException;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import com.cocacola.eventintelligence.repository.EventRepository;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import com.cocacola.eventintelligence.repository.RegistrationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
