package com.cocacola.eventintelligence.dto;

import java.time.OffsetDateTime;

public record CheckInResponse(
        Long attendanceId,
        Long eventId,
        Long participantId,
        String eventName,
        String participantName,
        String status,
        OffsetDateTime checkInAt) {
}
