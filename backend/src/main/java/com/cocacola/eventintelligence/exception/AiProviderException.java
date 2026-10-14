package com.cocacola.eventintelligence.exception;

public class AiProviderException extends RuntimeException {
    private static final String DEFAULT_CLIENT_MESSAGE =
            "No se pudo completar la operación con el proveedor externo.";

    private final String clientMessage;

    public AiProviderException(String message) {
        super(message);
        this.clientMessage = DEFAULT_CLIENT_MESSAGE;
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
        this.clientMessage = DEFAULT_CLIENT_MESSAGE;
    }

    public AiProviderException(String message, String clientMessage, Throwable cause) {
        super(message, cause);
        this.clientMessage = clientMessage == null || clientMessage.isBlank()
                ? DEFAULT_CLIENT_MESSAGE
                : clientMessage;
    }

    public String getClientMessage() {
        return clientMessage;
    }
}
