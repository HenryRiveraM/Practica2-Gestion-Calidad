package com.cocacola.eventintelligence.exception;

public class DuplicateSurveyException extends RuntimeException {
    public DuplicateSurveyException(Long eventId, Long participantId) {
        super("El participante " + participantId + " ya envió una encuesta para el evento " + eventId);
    }
}
