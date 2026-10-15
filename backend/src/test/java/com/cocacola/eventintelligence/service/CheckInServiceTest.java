package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.dto.CheckInRequest;
import com.cocacola.eventintelligence.dto.CheckInResponse;
import com.cocacola.eventintelligence.entity.Attendance;
import com.cocacola.eventintelligence.entity.Registration;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.exception.DuplicateCheckInException;
import com.cocacola.eventintelligence.exception.InvalidRegistrationTokenException;
import com.cocacola.eventintelligence.repository.AttendanceRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {
    private static final String TOKEN = "123e4567-e89b-12d3-a456-426614174000";

    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ParticipantRepository participantRepository;
    @InjectMocks
    private CheckInService checkInService;

    private Registration registration;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(checkInService, "access", org.mockito.Mockito.mock(com.cocacola.eventintelligence.security.AccessPolicy.class));
        registration = new Registration();
        registration.setId(1L);
        registration.setEventId(4L);
        registration.setParticipantId(8L);
    }

    private void stubRegistrationDetails() {
        com.cocacola.eventintelligence.entity.Event event = new com.cocacola.eventintelligence.entity.Event();
        event.setId(4L);
        event.setName("Evento de prueba");
        when(eventRepository.findById(4L)).thenReturn(Optional.of(event));
        com.cocacola.eventintelligence.entity.Participant participant =
                new com.cocacola.eventintelligence.entity.Participant();
        participant.setId(8L);
        participant.setFirstName("Ana");
        participant.setLastName("Pérez");
        when(participantRepository.findById(8L)).thenReturn(Optional.of(participant));
    }

    @Test
    void shouldCheckInRegistrationAndSetServerTimestamp() {
        stubRegistrationDetails();
        when(registrationRepository.findByToken(TOKEN)).thenReturn(Optional.of(registration));
        when(attendanceRepository.findByEventIdAndParticipantId(4L, 8L)).thenReturn(Optional.empty());
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Attendance attendance = invocation.getArgument(0);
            attendance.setId(11L);
            return attendance;
        });
        OffsetDateTime beforeRequest = OffsetDateTime.now();

        CheckInResponse response = checkInService.checkIn(new CheckInRequest(TOKEN));

        OffsetDateTime afterRequest = OffsetDateTime.now();
        assertEquals(11L, response.attendanceId());
        assertEquals(4L, response.eventId());
        assertEquals(8L, response.participantId());
        assertEquals("Evento de prueba", response.eventName());
        assertEquals("Ana Pérez", response.participantName());
        assertEquals("CHECKED_IN", response.status());
        assertNotNull(response.checkInAt());
        org.junit.jupiter.api.Assertions.assertFalse(response.checkInAt().isBefore(beforeRequest));
        org.junit.jupiter.api.Assertions.assertFalse(response.checkInAt().isAfter(afterRequest));
        verify(attendanceRepository).saveAndFlush(any(Attendance.class));
    }

    @Test
    void shouldUpdateExistingAttendanceToCheckedIn() {
        stubRegistrationDetails();
        when(registrationRepository.findByToken(TOKEN)).thenReturn(Optional.of(registration));
        Attendance existing = new Attendance();
        existing.setId(12L);
        existing.setEventId(4L);
        existing.setParticipantId(8L);
        existing.setStatus("REGISTERED");
        when(attendanceRepository.findByEventIdAndParticipantId(4L, 8L)).thenReturn(Optional.of(existing));
        when(attendanceRepository.saveAndFlush(existing)).thenReturn(existing);

        CheckInResponse response = checkInService.checkIn(new CheckInRequest(TOKEN));

        assertEquals(12L, response.attendanceId());
        assertEquals("CHECKED_IN", existing.getStatus());
        assertNotNull(existing.getCheckInAt());
        assertEquals("QR", existing.getCheckInMethod());
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThrows(com.cocacola.eventintelligence.exception.BusinessRuleException.class,
                () -> checkInService.checkIn(new CheckInRequest("not-a-uuid")));

        verify(registrationRepository, never()).findByToken(any());
    }

    @Test
    void shouldRejectValidTokenWithoutRegistration() {
        String unknownToken = UUID.randomUUID().toString();
        when(registrationRepository.findByToken(unknownToken)).thenReturn(Optional.empty());

        assertThrows(InvalidRegistrationTokenException.class,
                () -> checkInService.checkIn(new CheckInRequest(unknownToken)));
        verify(attendanceRepository, never()).findByEventIdAndParticipantId(any(), any());
    }

    @Test
    void shouldRejectCheckInWhenAttendanceIsAlreadyCheckedIn() {
        stubRegistrationDetails();
        when(registrationRepository.findByToken(TOKEN)).thenReturn(Optional.of(registration));
        Attendance existing = new Attendance();
        existing.setId(11L);
        existing.setEventId(4L);
        existing.setParticipantId(8L);
        existing.setStatus("CHECKED_IN");
        when(attendanceRepository.findByEventIdAndParticipantId(4L, 8L)).thenReturn(Optional.of(existing));

        assertThrows(DuplicateCheckInException.class,
                () -> checkInService.checkIn(new CheckInRequest(TOKEN)));
        verify(attendanceRepository, never()).saveAndFlush(any(Attendance.class));
    }

    @Test
    void shouldRejectRegistrationWhenItsEventNoLongerExists() {
        when(registrationRepository.findByToken(TOKEN)).thenReturn(Optional.of(registration));
        when(eventRepository.findById(4L)).thenReturn(Optional.empty());

        assertThrows(InvalidRegistrationTokenException.class,
                () -> checkInService.checkIn(new CheckInRequest(TOKEN)));
        verify(attendanceRepository, never()).findByEventIdAndParticipantId(any(), any());
    }

    // ---------------------------------------------------------------------
    // Tarjeta P2-14: pruebas nuevas P2-14-C01 .. P2-14-C06 (patrón AAA)
    // ---------------------------------------------------------------------

    /** P2-14-C01: un token nulo se rechaza sin consultar ningún repositorio. */
    @Test
    void shouldRejectNullTokenWithoutQueryingRepositories() {
        // Arrange
        CheckInRequest request = new CheckInRequest(null);

        // Act
        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                () -> checkInService.checkIn(request));

        // Assert
        assertEquals("El token QR no es válido", exception.getMessage());
        verifyNoInteractions(registrationRepository, attendanceRepository, eventRepository, participantRepository);
    }

    /** P2-14-C02: extrae el UUID de un texto con prefijo y lo normaliza a minúsculas antes de buscar. */
    @Test
    void shouldExtractAndNormalizeUuidFromPrefixedText() {
        // Arrange
        String scannedText = "Token QR: 123E4567-E89B-12D3-A456-426614174000 (copiado)";
        when(registrationRepository.findByToken(TOKEN)).thenReturn(Optional.empty());

        // Act
        InvalidRegistrationTokenException exception = assertThrows(InvalidRegistrationTokenException.class,
                () -> checkInService.checkIn(new CheckInRequest(scannedText)));

        // Assert
        assertEquals("No existe una inscripción para este QR", exception.getMessage());
        verify(registrationRepository).findByToken(TOKEN);
    }

    /** P2-14-C03: se rechaza una inscripción cuyo participante ya no existe. */
    @Test
    void shouldRejectRegistrationWhenItsParticipantNoLongerExists() {
        // Arrange
        com.cocacola.eventintelligence.entity.Event event = new com.cocacola.eventintelligence.entity.Event();
        event.setId(4L);
        when(registrationRepository.findByToken(TOKEN)).thenReturn(Optional.of(registration));
        when(eventRepository.findById(4L)).thenReturn(Optional.of(event));
        when(participantRepository.findById(8L)).thenReturn(Optional.empty());

        // Act
        InvalidRegistrationTokenException exception = assertThrows(InvalidRegistrationTokenException.class,
                () -> checkInService.checkIn(new CheckInRequest(TOKEN)));

        // Assert
        assertEquals("El participante de esta inscripción no existe", exception.getMessage());
        verify(attendanceRepository, never()).findByEventIdAndParticipantId(any(), any());
        verify(attendanceRepository, never()).saveAndFlush(any(Attendance.class));
    }

    /** P2-14-C04: uk_attendances_event_participant se traduce a DuplicateCheckInException. */
    @Test
    void shouldTranslateUkAttendanceConstraintToDuplicateCheckIn() {
        // Arrange
        stubCheckInUntilSave();
        when(attendanceRepository.saveAndFlush(any(Attendance.class)))
                .thenThrow(integrityViolationCausedByConstraint("uk_attendances_event_participant"));

        // Act
        DuplicateCheckInException exception = assertThrows(DuplicateCheckInException.class,
                () -> checkInService.checkIn(new CheckInRequest(TOKEN)));

        // Assert
        assertEquals("Esta inscripción ya realizó el check-in", exception.getMessage());
        verify(attendanceRepository).saveAndFlush(any(Attendance.class));
    }

    /** P2-14-C05: uq_attendances_event_participant (nombre alterno) se traduce a DuplicateCheckInException. */
    @Test
    void shouldTranslateUqAttendanceConstraintToDuplicateCheckIn() {
        // Arrange
        stubCheckInUntilSave();
        when(attendanceRepository.saveAndFlush(any(Attendance.class)))
                .thenThrow(integrityViolationCausedByConstraint("uq_attendances_event_participant"));

        // Act
        DuplicateCheckInException exception = assertThrows(DuplicateCheckInException.class,
                () -> checkInService.checkIn(new CheckInRequest(TOKEN)));

        // Assert
        assertEquals("Esta inscripción ya realizó el check-in", exception.getMessage());
        verify(attendanceRepository).saveAndFlush(any(Attendance.class));
    }

    /** P2-14-C06: una violación de integridad ajena a la asistencia duplicada se propaga sin traducir. */
    @Test
    void shouldPropagateIntegrityViolationUnrelatedToDuplicateAttendance() {
        // Arrange
        stubCheckInUntilSave();
        DataIntegrityViolationException otherViolation =
                integrityViolationCausedByConstraint("fk_attendances_participant");
        when(attendanceRepository.saveAndFlush(any(Attendance.class))).thenThrow(otherViolation);

        // Act
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> checkInService.checkIn(new CheckInRequest(TOKEN)));

        // Assert
        assertSame(otherViolation, thrown);
    }

    private void stubCheckInUntilSave() {
        stubRegistrationDetails();
        when(registrationRepository.findByToken(TOKEN)).thenReturn(Optional.of(registration));
        when(attendanceRepository.findByEventIdAndParticipantId(4L, 8L)).thenReturn(Optional.empty());
    }

    private DataIntegrityViolationException integrityViolationCausedByConstraint(String constraintName) {
        org.hibernate.exception.ConstraintViolationException hibernateViolation =
                new org.hibernate.exception.ConstraintViolationException(
                        "could not execute statement", new SQLException("violación de restricción"), constraintName);
        return new DataIntegrityViolationException("error de integridad",
                new RuntimeException("envoltorio", hibernateViolation));
    }
}