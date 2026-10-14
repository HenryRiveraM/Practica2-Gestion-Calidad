package com.cocacola.eventintelligence.dto;

public record RegistrationResponse(
        Long registrationId,
        Long eventId,
        String eventName,
        Long participantId,
        String participantName,
        String qrToken) {
}
