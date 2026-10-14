package com.cocacola.eventintelligence.exception;

public class DuplicateRegistrationException extends RuntimeException {
    public DuplicateRegistrationException(Long eventId, Long participantId) {
        super("El participante " + participantId + " ya está registrado en el evento " + eventId);
    }
}
