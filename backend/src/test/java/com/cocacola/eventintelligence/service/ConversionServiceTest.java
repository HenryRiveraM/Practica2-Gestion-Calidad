package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Conversion;
import com.cocacola.eventintelligence.repository.ConversionRepository;
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
 * Debe estar en el mismo paquete que ConversionService para poder
 * invocar el método protegido validate(...).
 *
 * Ajusta el tipo de los IDs (Long) si en tu entidad son otro tipo.
 */
@ExtendWith(MockitoExtension.class)
class ConversionServiceTest {

    private static final Long EVENT_ID = 10L;
    private static final Long PARTICIPANT_ID = 20L;

    @Mock
    private ConversionRepository repository;

    @Mock
    private AttendeeGuard attendeeGuard;

    private ConversionService service;

    @BeforeEach
    void setUp() {
        service = new ConversionService(repository, attendeeGuard);
    }

    // ---- Constructor (líneas 12-14) ----

    @Test
    void constructor_createsInstanceWithDependencies() {
        ConversionService created = new ConversionService(repository, attendeeGuard);

        assertNotNull(created);
    }

    // ---- validate (línea 18) ----

    @Test
    void validate_delegatesToAttendeeGuardWithEventAndParticipantIds() {
        Conversion entity = mock(Conversion.class);
        when(entity.getEventId()).thenReturn(EVENT_ID);
        when(entity.getParticipantId()).thenReturn(PARTICIPANT_ID);

        assertDoesNotThrow(() -> service.validate(entity));

        verify(attendeeGuard).requireCheckedIn(EVENT_ID, PARTICIPANT_ID);
        verifyNoMoreInteractions(attendeeGuard);
    }

    @Test
    void validate_propagatesExceptionWhenAttendeeIsNotCheckedIn() {
        Conversion entity = mock(Conversion.class);
        when(entity.getEventId()).thenReturn(EVENT_ID);
        when(entity.getParticipantId()).thenReturn(PARTICIPANT_ID);

        IllegalStateException expected = new IllegalStateException("No ha hecho check-in");
        doThrow(expected).when(attendeeGuard).requireCheckedIn(EVENT_ID, PARTICIPANT_ID);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> service.validate(entity));

        assertSame(expected, thrown);
        verify(attendeeGuard).requireCheckedIn(EVENT_ID, PARTICIPANT_ID);
    }

    @Test
    void validate_doesNotTouchRepository() {
        Conversion entity = mock(Conversion.class);
        when(entity.getEventId()).thenReturn(EVENT_ID);
        when(entity.getParticipantId()).thenReturn(PARTICIPANT_ID);

        service.validate(entity);

        verify(repository, never()).save(entity);
    }
}