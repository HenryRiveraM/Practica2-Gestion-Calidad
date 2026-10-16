package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import com.cocacola.eventintelligence.repository.EventRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas unitarias de EventService (create, update y synchronizePublication).
 * EventRepository y JdbcTemplate están simulados: no hay base de datos real, y los mocks
 * de JdbcTemplate no validan el SQL, solo que se emita el comando esperado.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {
    private static final String LOCK_SQL = "SELECT id FROM events WHERE id=? FOR UPDATE";
    private static final String ENABLE_FRAGMENT = "INSERT INTO event_public_access";
    private static final String DISABLE_SQL = "UPDATE event_public_access SET enabled=FALSE WHERE event_id=?";

    @Mock
    private EventRepository events;
    @Mock
    private JdbcTemplate jdbc;
    @InjectMocks
    private EventService service;

    // ---- create ------------------------------------------------------------

    /** create: guarda, hace flush y sincroniza la publicación (en ese orden) para un evento ACTIVE. */
    @Test
    void shouldSaveFlushAndEnablePublicAccessWhenCreatingActiveEvent() {
        // Arrange
        Event input = event(null, "ACTIVE");
        when(events.save(any(Event.class))).thenAnswer(invocation -> {
            Event toSave = invocation.getArgument(0);
            toSave.setId(7L);
            return toSave;
        });
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);

        // Act
        Event created = service.create(input);

        // Assert
        assertSame(input, created);
        assertEquals(7L, created.getId());
        InOrder order = inOrder(events, jdbc);
        order.verify(events).save(input);
        order.verify(events).flush();
        order.verify(jdbc).update(contains(ENABLE_FRAGMENT), eq(7L), token.capture());
        assertDoesNotThrow(() -> UUID.fromString(token.getValue()));
    }

    /** create (heredado de AbstractCrudService): descarta el id recibido antes de guardar. */
    @Test
    void shouldClearProvidedIdBeforeSavingNewEvent() {
        // Arrange
        Event input = event(99L, "PLANNED");
        AtomicReference<Long> idAtSave = new AtomicReference<>(-1L);
        when(events.save(any(Event.class))).thenAnswer(invocation -> {
            Event toSave = invocation.getArgument(0);
            idAtSave.set(toSave.getId());
            toSave.setId(7L);
            return toSave;
        });

        // Act
        service.create(input);

        // Assert
        assertNull(idAtSave.get());
        verify(events).flush();
    }

    /** create: un evento que no está ACTIVE, COMPLETED ni CANCELLED no toca event_public_access. */
    @Test
    void shouldNotTouchPublicAccessWhenCreatedEventIsPlanned() {
        // Arrange
        Event input = event(null, "PLANNED");
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.create(input);

        // Assert
        verify(events).save(input);
        verify(events).flush();
        verifyNoInteractions(jdbc);
    }

    /** create: un evento creado como CANCELLED deshabilita el acceso público. */
    @Test
    void shouldDisablePublicAccessWhenEventIsCreatedAsCancelled() {
        // Arrange
        Event input = event(null, "CANCELLED");
        when(events.save(any(Event.class))).thenAnswer(invocation -> {
            Event toSave = invocation.getArgument(0);
            toSave.setId(7L);
            return toSave;
        });

        // Act
        service.create(input);

        // Assert
        verify(jdbc).update(eq(DISABLE_SQL), eq(7L));
        verify(jdbc, never()).update(contains(ENABLE_FRAGMENT), eq(7L), anyString());
    }

    // ---- update ------------------------------------------------------------

    /** update: bloquea la fila, consulta el estado anterior, guarda, hace flush y activa el acceso (PLANNED -> ACTIVE). */
    @Test
    void shouldLockReadPreviousStatusSaveFlushAndEnableAccessWhenEventBecomesActive() {
        // Arrange
        Event existing = event(5L, "PLANNED");
        Event incoming = event(null, "ACTIVE");
        when(events.findById(5L)).thenReturn(Optional.of(existing));
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);

        // Act
        Event updated = service.update(5L, incoming);

        // Assert
        assertSame(incoming, updated);
        assertEquals(5L, updated.getId());
        InOrder order = inOrder(jdbc, events);
        order.verify(jdbc).queryForList(LOCK_SQL, 5L);
        order.verify(events, times(2)).findById(5L);
        order.verify(events).save(incoming);
        order.verify(events).flush();
        order.verify(jdbc).update(contains(ENABLE_FRAGMENT), eq(5L), token.capture());
        assertDoesNotThrow(() -> UUID.fromString(token.getValue()));
    }

    /** update (heredado): el id de la ruta prevalece sobre el id del cuerpo. */
    @Test
    void shouldUsePathIdInsteadOfBodyIdWhenUpdating() {
        // Arrange
        Event incoming = event(999L, "PLANNED");
        when(events.findById(5L)).thenReturn(Optional.of(event(5L, "PLANNED")));
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Event updated = service.update(5L, incoming);

        // Assert
        assertEquals(5L, updated.getId());
        verify(events).save(incoming);
    }

    /** synchronizePublication: ACTIVE -> ACTIVE no vuelve a generar token ni modifica event_public_access. */
    @Test
    void shouldNotModifyPublicAccessWhenEventWasAlreadyActive() {
        // Arrange
        when(events.findById(5L)).thenReturn(Optional.of(event(5L, "ACTIVE")));
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.update(5L, event(null, "ACTIVE"));

        // Assert
        verify(jdbc).queryForList(LOCK_SQL, 5L);
        verify(events).flush();
        verifyNoMoreInteractions(jdbc);
    }

    /** synchronizePublication: pasar a COMPLETED o CANCELLED deshabilita el acceso público (desde ACTIVE o no). */
    @ParameterizedTest(name = "{0} -> {1} deshabilita el acceso público")
    @CsvSource({
            "ACTIVE, COMPLETED",
            "ACTIVE, CANCELLED",
            "PLANNED, COMPLETED",
            "PLANNED, CANCELLED"
    })
    void shouldDisablePublicAccessWhenEventBecomesCompletedOrCancelled(String previous, String next) {
        // Arrange
        when(events.findById(5L)).thenReturn(Optional.of(event(5L, previous)));
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.update(5L, event(null, next));

        // Assert
        verify(jdbc).update(eq(DISABLE_SQL), eq(5L));
        verify(jdbc, never()).update(contains(ENABLE_FRAGMENT), eq(5L), anyString());
    }

    /** synchronizePublication: estados que no cumplen ninguna condición no provocan cambios en event_public_access. */
    @ParameterizedTest(name = "{0} -> {1} no modifica event_public_access")
    @CsvSource({
            "ACTIVE, PLANNED",
            "PLANNED, PLANNED",
            "COMPLETED, PLANNED"
    })
    void shouldNotModifyPublicAccessWhenNewStatusDoesNotRequireSynchronization(String previous, String next) {
        // Arrange
        when(events.findById(5L)).thenReturn(Optional.of(event(5L, previous)));
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.update(5L, event(null, next));

        // Assert
        verify(events).flush();
        verify(jdbc).queryForList(LOCK_SQL, 5L);
        verifyNoMoreInteractions(jdbc);
    }

    /** synchronizePublication: un evento CANCELLED que vuelve a ACTIVE reactiva el acceso público. */
    @Test
    void shouldEnablePublicAccessAgainWhenCancelledEventBecomesActive() {
        // Arrange
        when(events.findById(5L)).thenReturn(Optional.of(event(5L, "CANCELLED")));
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.update(5L, event(null, "ACTIVE"));

        // Assert
        verify(jdbc).update(contains(ENABLE_FRAGMENT), eq(5L), anyString());
        verify(jdbc, never()).update(eq(DISABLE_SQL), eq(5L));
    }

    /** update: si el evento no existe se informa y no se guarda ni se sincroniza nada. */
    @Test
    void shouldRejectUpdateWhenEventDoesNotExist() {
        // Arrange
        when(events.findById(5L)).thenReturn(Optional.empty());
        Event incoming = event(null, "ACTIVE");

        // Act
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> service.update(5L, incoming));

        // Assert
        assertEquals("Evento con id 5 no existe", exception.getMessage());
        verify(jdbc).queryForList(LOCK_SQL, 5L);
        verifyNoMoreInteractions(jdbc);
        verify(events, never()).save(any(Event.class));
        verify(events, never()).flush();
    }

    private static Event event(Long id, String status) {
        Event event = new Event();
        event.setId(id);
        event.setName("Evento de prueba");
        event.setStatus(status);
        return event;
    }
}