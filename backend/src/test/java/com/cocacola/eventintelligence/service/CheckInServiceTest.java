package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.dto.CheckInRequest;
import com.cocacola.eventintelligence.dto.CheckInResponse;
import com.cocacola.eventintelligence.entity.Attendance;
import com.cocacola.eventintelligence.entity.Registration;
import com.cocacola.eventintelligence.exception.DuplicateCheckInException;
import com.cocacola.eventintelligence.exception.InvalidRegistrationTokenException;
import com.cocacola.eventintelligence.repository.AttendanceRepository;
import com.cocacola.eventintelligence.repository.EventRepository;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import com.cocacola.eventintelligence.repository.RegistrationRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}