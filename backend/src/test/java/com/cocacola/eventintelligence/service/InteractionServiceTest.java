package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Interaction;
import com.cocacola.eventintelligence.repository.InteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Debe estar en el mismo paquete que InteractionService para poder
 * invocar el método protegido validate(...).
 *
 * Ajusta el tipo de los IDs (Long) si en tu entidad son otro tipo (UUID, String, etc.).
 */
@ExtendWith(MockitoExtension.class)
class InteractionServiceTest {

    private static final Long EVENT_ID = 10L;
    private static final Long PARTICIPANT_ID = 20L;

    @Mock
    private InteractionRepository repository;

    @Mock
    private AttendeeGuard attendeeGuard;

    private InteractionService service;

    @BeforeEach
    void setUp() {
        service = new InteractionService(repository, attendeeGuard);
    }

    // ---- Constructor (líneas 12-14) ----

    @Test
    void constructor_createsInstanceWithDependencies() {
        InteractionService created = new InteractionService(repository, attendeeGuard);

        assertNotNull(created);
    }

    // ---- validate (línea 18) ----

    @Test
    void validate_delegatesToAttendeeGuardWithEventAndParticipantIds() {
        Interaction interaction = mock(Interaction.class);
        when(interaction.getEventId()).thenReturn(EVENT_ID);
        when(interaction.getParticipantId()).thenReturn(PARTICIPANT_ID);

        assertDoesNotThrow(() -> service.validate(interaction));

        verify(attendeeGuard).requireCheckedIn(EVENT_ID, PARTICIPANT_ID);
        verifyNoMoreInteractions(attendeeGuard);
    }

    @Test
    void validate_propagatesExceptionWhenAttendeeIsNotCheckedIn() {
        Interaction interaction = mock(Interaction.class);
        when(interaction.getEventId()).thenReturn(EVENT_ID);
        when(interaction.getParticipantId()).thenReturn(PARTICIPANT_ID);

        IllegalStateException expected = new IllegalStateException("No ha hecho check-in");
        doThrow(expected).when(attendeeGuard).requireCheckedIn(EVENT_ID, PARTICIPANT_ID);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> service.validate(interaction));

        assertSame(expected, thrown);
        verify(attendeeGuard).requireCheckedIn(EVENT_ID, PARTICIPANT_ID);
    }

    @Test
    void validate_doesNotTouchRepository() {
        Interaction interaction = mock(Interaction.class);
        when(interaction.getEventId()).thenReturn(EVENT_ID);
        when(interaction.getParticipantId()).thenReturn(PARTICIPANT_ID);

        service.validate(interaction);

        verify(repository, never()).save(interaction);
    }
}