package com.phase.api.common;

import com.phase.api.phase.PhaseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(
                ex.getStatus().value(),
                ex.getCode(),
                ex.getMessage(),
                request.getRequestURI(),
                requestId(request),
                ex.getDetails()
        ));
    }

    @ExceptionHandler(PhaseException.class)
    public ResponseEntity<ApiError> handlePhase(PhaseException ex, HttpServletRequest request) {
        HttpStatus status = mapPhaseStatus(ex.getCode());
        if (status.is5xxServerError()) {
            log.error("Error de PhaseDB code={} message={} requestId={}",
                    ex.getCode(), ex.getMessage(), ex.getRequestId(), ex);
        }
        return ResponseEntity.status(status).body(ApiError.of(
                status.value(),
                ex.getCode() == null ? "PHASE_ERROR" : ex.getCode(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getRequestId() == null ? requestId(request) : ex.getRequestId(),
                null
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.unprocessableEntity().body(ApiError.of(
                422,
                "VALIDACION",
                "Los datos enviados no son validos",
                request.getRequestURI(),
                requestId(request),
                details
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex,
                                                     HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                details.put(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.unprocessableEntity().body(ApiError.of(
                422,
                "VALIDACION",
                "Los datos enviados no son validos",
                request.getRequestURI(),
                requestId(request),
                details
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                400,
                "JSON_INVALIDO",
                "El cuerpo de la peticion no es JSON valido",
                request.getRequestURI(),
                requestId(request),
                null
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                403,
                "SIN_PERMISO",
                "No tienes permisos para esta operacion",
                request.getRequestURI(),
                requestId(request),
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Error no controlado requestId={} path={}", requestId, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                500,
                "ERROR_INTERNO",
                "Ocurrio un error inesperado",
                request.getRequestURI(),
                requestId,
                null
        ));
    }

    private HttpStatus mapPhaseStatus(String code) {
        if (code == null) {
            return HttpStatus.BAD_GATEWAY;
        }
        return switch (code.toUpperCase()) {
            case "UNDEFINED VARIABLE" -> HttpStatus.NOT_FOUND;
            case "PROTECT" -> HttpStatus.FORBIDDEN;
            case "CONFLICT", "USER EXISTS" -> HttpStatus.CONFLICT;
            case "TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "SYNTAX" -> HttpStatus.INTERNAL_SERVER_ERROR;
            case "HTTP_401", "HTTP_403" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "PHASE_CONNECTION", "PHASE_EMPTY", "HTTP_500", "HTTP_502", "HTTP_503" ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private String requestId(HttpServletRequest request) {
        Object phaseRequestId = request.getAttribute("phase.requestId");
        if (phaseRequestId != null) {
            return String.valueOf(phaseRequestId);
        }
        return UUID.randomUUID().toString();
    }
}
