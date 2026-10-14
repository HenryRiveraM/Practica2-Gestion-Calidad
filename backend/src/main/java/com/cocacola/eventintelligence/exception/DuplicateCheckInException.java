package com.cocacola.eventintelligence.exception;

public class DuplicateCheckInException extends RuntimeException {
    public DuplicateCheckInException() {
        super("Esta inscripción ya realizó el check-in");
    }
}
