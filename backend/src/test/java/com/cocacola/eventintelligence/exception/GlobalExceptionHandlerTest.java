package com.cocacola.eventintelligence.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Las excepciones de dominio se mockean para controlar getMessage() sin depender de
 * sus constructores. AiProviderException se usa real porque el handler la registra en
 * el log junto con su traza, y un mock no tiene stack trace.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ---- handlers con safeMessage (mensaje propio o valor por defecto) ----

    @Test
    void handleNotFound_usesExceptionMessageOrFallback() {
        assertSafeMessage(
                message -> handler.handleNotFound(exceptionWithMessage(ResourceNotFoundException.class, message), request),
                HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @Test
    void handleBusinessRule_usesExceptionMessageOrFallback() {
        assertSafeMessage(
                message -> handler.handleBusinessRule(exceptionWithMessage(BusinessRuleException.class, message), request),
                HttpStatus.BAD_REQUEST, "Solicitud inválida");
    }

    @Test
    void handleDuplicateRegistration_usesExceptionMessageOrFallback() {
        assertSafeMessage(
                message -> handler.handleDuplicateRegistration(
                        exceptionWithMessage(DuplicateRegistrationException.class, message), request),
                HttpStatus.CONFLICT, "La inscripción ya existe");
    }

    @Test
    void handleInvalidRegistrationToken_usesExceptionMessageOrFallback() {
        assertSafeMessage(
                message -> handler.handleInvalidRegistrationToken(
                        exceptionWithMessage(InvalidRegistrationTokenException.class, message), request),
                HttpStatus.NOT_FOUND, "Inscripción no encontrada");
    }

    @Test
    void handleDuplicateCheckIn_usesExceptionMessageOrFallback() {
        assertSafeMessage(
                message -> handler.handleDuplicateCheckIn(
                        exceptionWithMessage(DuplicateCheckInException.class, message), request),
                HttpStatus.CONFLICT, "El check-in ya fue realizado");
    }

    @Test
    void handleDuplicateSurvey_usesExceptionMessageOrFallback() {
        assertSafeMessage(
                message -> handler.handleDuplicateSurvey(
                        exceptionWithMessage(DuplicateSurveyException.class, message), request),
                HttpStatus.CONFLICT, "La encuesta ya existe");
    }

    // ---- handlers con mensaje fijo ----

    @Test
    void handleEntityNotFound_returns404ForJpaAndHibernateExceptions() {
        assertError(handler.handleEntityNotFound(new EntityNotFoundException("detalle interno"), request),
                HttpStatus.NOT_FOUND, "Recurso no encontrado");
        assertError(handler.handleEntityNotFound(mock(ObjectNotFoundException.class), request),
                HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @Test
    void handleIllegalArgument_returns400WithoutLeakingTheMessage() {
        assertError(handler.handleIllegalArgument(new IllegalArgumentException("detalle interno"), request),
                HttpStatus.BAD_REQUEST, "Solicitud inválida.");
    }

    @Test
    void handleDataIntegrityViolation_returns409AndLogsRequestUri() {
        when(request.getRequestURI()).thenReturn("/api/events");
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "error de persistencia", new IllegalStateException("llave duplicada"));

        assertError(handler.handleDataIntegrityViolation(ex, request),
                HttpStatus.CONFLICT, "La operación entra en conflicto con los datos existentes.");
        verify(request).getRequestURI();
    }

    @Test
    void handleUnreadableMessage_returns400() {
        assertError(handler.handleUnreadableMessage(mock(HttpMessageNotReadableException.class), request),
                HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud está vacío o tiene un formato inválido.");
    }

    @Test
    void handleAiProviderError_returns502WithClientMessage() {
        when(request.getRequestURI()).thenReturn("/api/events/9/insights");
        AiProviderException ex = new AiProviderException("Gemini no respondió.");

        ResponseEntity<Map<String, Object>> response = handler.handleAiProviderError(ex, request);

        assertError(response, HttpStatus.BAD_GATEWAY, ex.getClientMessage());
        verify(request).getRequestURI();
    }

    // ---- validaciones ----

    @Test
    void handleValidation_joinsFieldErrorsInOrder() {
        MethodArgumentNotValidException ex = validationException(List.of(
                new FieldError("body", "name", "no debe estar vacío"),
                new FieldError("body", "email", "formato inválido")));

        assertError(handler.handleValidation(ex, request),
                HttpStatus.BAD_REQUEST, "{name=no debe estar vacío, email=formato inválido}");
    }

    @Test
    void handleValidation_keepsLastMessageWhenSameFieldFailsTwice() {
        MethodArgumentNotValidException ex = validationException(List.of(
                new FieldError("body", "name", "primer error"),
                new FieldError("body", "name", "segundo error")));

        assertError(handler.handleValidation(ex, request),
                HttpStatus.BAD_REQUEST, "{name=segundo error}");
    }

    @Test
    void handleValidation_usesGenericMessageWhenThereAreNoFieldErrors() {
        MethodArgumentNotValidException ex = validationException(List.of());

        assertError(handler.handleValidation(ex, request), HttpStatus.BAD_REQUEST, "Validación fallida");
    }

    @Test
    void handleConstraintViolation_returnsFirstMessageInAlphabeticalOrder() {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.add(violation("z debe ser positivo"));
        violations.add(violation("a no debe estar vacío"));
        violations.add(violation("m es obligatorio"));

        assertError(handler.handleConstraintViolation(new ConstraintViolationException(violations), request),
                HttpStatus.BAD_REQUEST, "a no debe estar vacío");
    }

    @Test
    void handleConstraintViolation_usesGenericMessageWhenThereAreNoViolations() {
        ConstraintViolationException ex = new ConstraintViolationException(new HashSet<ConstraintViolation<?>>());

        assertError(handler.handleConstraintViolation(ex, request), HttpStatus.BAD_REQUEST, "Validación fallida");
    }

    // ---- seguridad y ResponseStatusException ----

    @Test
    void denied_returns403() {
        assertError(handler.denied(new AccessDeniedException("sin permiso")),
                HttpStatus.FORBIDDEN, "No tienes permiso para esta operación o evento.");
    }

    @Test
    void status_returnsTheStatusAndReasonOfTheException() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos.");

        assertError(handler.status(ex), HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos.");
    }

    @Test
    void status_allowsMissingReason() {
        assertError(handler.status(new ResponseStatusException(HttpStatus.CONFLICT)), HttpStatus.CONFLICT, null);
    }

    // ---- handler genérico ----

    @Test
    void handleGeneral_returns500WithoutLeakingDetails() {
        assertError(handler.handleGeneral(new IllegalStateException("secreto interno"), request),
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    @Test
    void handleGeneral_handlesExceptionsWithoutStackTrace() {
        Exception ex = new RuntimeException("sin traza");
        ex.setStackTrace(new StackTraceElement[0]);

        assertError(handler.handleGeneral(ex, request),
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    // ---- helpers ----

    private static void assertSafeMessage(
            Function<String, ResponseEntity<Map<String, Object>>> call, HttpStatus status, String fallback) {
        assertError(call.apply("Mensaje específico"), status, "Mensaje específico");
        assertError(call.apply(null), status, fallback);
        assertError(call.apply("   "), status, fallback);
    }

    private static <E extends Exception> E exceptionWithMessage(Class<E> type, String message) {
        E exception = mock(type);
        when(exception.getMessage()).thenReturn(message);
        return exception;
    }

    private static MethodArgumentNotValidException validationException(List<FieldError> fieldErrors) {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);
        return ex;
    }

    private static ConstraintViolation<?> violation(String message) {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    private static void assertError(ResponseEntity<Map<String, Object>> response, HttpStatus status, String message) {
        assertEquals(status.value(), response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(status.value(), body.get("status"));
        assertEquals(message, body.get("message"));
        assertInstanceOf(OffsetDateTime.class, body.get("timestamp"));
        assertEquals(List.of("timestamp", "status", "message"), new ArrayList<>(body.keySet()));
    }
}