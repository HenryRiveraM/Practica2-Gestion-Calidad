package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.repository.AttendanceRepository;
import org.springframework.stereotype.Component;

@Component
public class AttendeeGuard {
    private final AttendanceRepository attendanceRepository;

    public AttendeeGuard(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public void requireCheckedIn(Long eventId, Long participantId) {
        if (eventId == null || participantId == null
                || !attendanceRepository.existsByEventIdAndParticipantIdAndStatus(eventId, participantId, "CHECKED_IN")) {
            throw new BusinessRuleException("El participante debe tener check-in en el evento para registrar esta acción");
        }
    }
}