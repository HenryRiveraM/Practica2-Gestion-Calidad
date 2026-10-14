package com.cocacola.eventintelligence.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, safeMessage(ex.getMessage(), "Recurso no encontrado"));
    }

    @ExceptionHandler({EntityNotFoundException.class, ObjectNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(RuntimeException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, safeMessage(ex.getMessage(), "Solicitud inválida"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Solicitud inválida.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        logger.warn("Conflicto de integridad de datos en {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return buildError(HttpStatus.CONFLICT, "La operación entra en conflicto con los datos existentes.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud está vacío o tiene un formato inválido.");
    }

    @ExceptionHandler(DuplicateRegistrationException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateRegistration(
            DuplicateRegistrationException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, safeMessage(ex.getMessage(), "La inscripción ya existe"));
    }

    @ExceptionHandler(InvalidRegistrationTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRegistrationToken(
            InvalidRegistrationTokenException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, safeMessage(ex.getMessage(), "Inscripción no encontrada"));
    }

    @ExceptionHandler(DuplicateCheckInException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateCheckIn(
            DuplicateCheckInException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, safeMessage(ex.getMessage(), "El check-in ya fue realizado"));
    }

    @ExceptionHandler(DuplicateSurveyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSurvey(
            DuplicateSurveyException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, safeMessage(ex.getMessage(), "La encuesta ya existe"));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<Map<String, Object>> handleAiProviderError(
            AiProviderException ex, HttpServletRequest request) {
        logger.warn("El proveedor externo no pudo procesar la solicitud en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(HttpStatus.BAD_GATEWAY, ex.getClientMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String message = fieldErrors.isEmpty() ? "Validación fallida" : fieldErrors.toString();
        return buildError(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .sorted()
                .findFirst()
                .orElse("Validación fallida");
        return buildError(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String,Object>> denied(org.springframework.security.access.AccessDeniedException ex) {
        return buildError(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación o evento.");
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> status(org.springframework.web.server.ResponseStatusException ex) {
        return buildError(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, HttpServletRequest request) {
        StackTraceElement origin = ex.getStackTrace().length == 0 ? null : ex.getStackTrace()[0];
        logger.error("Error inesperado al procesar la solicitud. tipo={}, origen={}",
                ex.getClass().getSimpleName(), origin);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private static String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}