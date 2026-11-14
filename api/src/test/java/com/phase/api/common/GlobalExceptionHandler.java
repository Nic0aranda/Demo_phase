package com.phase.api.common;

import com.phase.api.phase.PhaseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS && ex.getDetails() != null) {
            String retryAfter = ex.getDetails().get("retryAfterSeconds");
            if (retryAfter != null) {
                builder.header(HttpHeaders.RETRY_AFTER, retryAfter);
            }
        }
        return builder.body(ApiError.of(
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
        } else {
            log.warn("PhaseDB rechazo la operacion code={} message={} requestId={}",
                    ex.getCode(), ex.getMessage(), ex.getRequestId());
        }
        return ResponseEntity.status(status).body(ApiError.of(
                status.value(),
                safePhaseCode(status),
                safePhaseMessage(status),
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                404,
                "NO_ENCONTRADO",
                "El recurso solicitado no existe",
                request.getRequestURI(),
                requestId(request),
                null
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException ex,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiError.of(
                413,
                "ARCHIVO_DEMASIADO_GRANDE",
                "El archivo supera el limite de 10 MB",
                request.getRequestURI(),
                requestId(request),
                Map.of("archivo", "El archivo supera el limite de 10 MB")
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

    private String safePhaseCode(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "NO_ENCONTRADO";
            case FORBIDDEN -> "SIN_PERMISO";
            case CONFLICT -> "CONFLICTO";
            case GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE -> "SERVICIO_DATOS_NO_DISPONIBLE";
            case INTERNAL_SERVER_ERROR -> "ERROR_INTERNO";
            default -> "ERROR_DATOS";
        };
    }

    private String safePhaseMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso no encontrado en PhaseDB";
            case FORBIDDEN -> "Operacion no permitida por PhaseDB";
            case CONFLICT -> "Conflicto al guardar los datos";
            case GATEWAY_TIMEOUT -> "El servicio de datos no respondio a tiempo";
            case SERVICE_UNAVAILABLE -> "Servicio de datos no disponible";
            case INTERNAL_SERVER_ERROR -> "Error interno al procesar los datos";
            default -> "Error al comunicarse con el servicio de datos";
        };
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (requestId != null) {
            return String.valueOf(requestId);
        }
        Object phaseRequestId = request.getAttribute("phase.requestId");
        if (phaseRequestId != null) {
            return String.valueOf(phaseRequestId);
        }
        return UUID.randomUUID().toString();
    }
}
