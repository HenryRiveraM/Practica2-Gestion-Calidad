package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.repository.AttendanceRepository;
import org.junit.jupiter.api.Test;
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
}
