package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.repository.AttendanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendeeGuardTest {
    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private AttendeeGuard attendeeGuard;

    @Test
    void shouldAllowCheckedInParticipant() {
        when(attendanceRepository.existsByEventIdAndParticipantIdAndStatus(1L, 2L, "CHECKED_IN")).thenReturn(true);

        assertDoesNotThrow(() -> attendeeGuard.requireCheckedIn(1L, 2L));
    }

    @Test
    void shouldRejectParticipantWithoutCheckedIn() {
        when(attendanceRepository.existsByEventIdAndParticipantIdAndStatus(1L, 2L, "CHECKED_IN")).thenReturn(false);

        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                () -> attendeeGuard.requireCheckedIn(1L, 2L));

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("check-in");
    }

    @Test
    @DisplayName("P2-15-C01: rechaza evento null sin consultar asistencia")
    void rejectsNullEventWithoutRepositoryCall() {
        assertThrows(BusinessRuleException.class, () -> attendeeGuard.requireCheckedIn(null, 2L));

        verifyNoInteractions(attendanceRepository);
    }

    @Test
    @DisplayName("P2-15-C02: rechaza participante null sin consultar asistencia")
    void rejectsNullParticipantWithoutRepositoryCall() {
        assertThrows(BusinessRuleException.class, () -> attendeeGuard.requireCheckedIn(1L, null));

        verifyNoInteractions(attendanceRepository);
    }

    @Test
    @DisplayName("P2-15-C03: rechaza ambos IDs null sin consultar asistencia")
    void rejectsBothNullIdsWithoutRepositoryCall() {
        assertThrows(BusinessRuleException.class, () -> attendeeGuard.requireCheckedIn(null, null));

        verifyNoInteractions(attendanceRepository);
    }

    @Test
    @DisplayName("P2-15-C04: propaga fallo técnico del repositorio")
    void propagatesRepositoryFailure() {
        var failure = new org.springframework.dao.DataAccessResourceFailureException("Base no disponible");
        when(attendanceRepository.existsByEventIdAndParticipantIdAndStatus(1L, 2L, "CHECKED_IN"))
                .thenThrow(failure);

        assertSame(failure, assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> attendeeGuard.requireCheckedIn(1L, 2L)));
    }
}
