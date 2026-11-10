package ar.edu.unicen.ticketya.inventory.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce excepciones al contrato de error RFC 7807 de TicketYa:
 * status, title, detail, instance, code y errors[].
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La solicitud tiene campos inválidos", errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, Object>> errors = ex.getConstraintViolations().stream()
                .map(violation -> Map.<String, Object>of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La solicitud tiene parámetros inválidos", errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "El cuerpo de la solicitud no es JSON válido", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no existe", List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "El método HTTP no está permitido para este recurso", List.of(), request);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleLockTimeout(PessimisticLockingFailureException ex,
                                                           HttpServletRequest request) {
        log.warn("No se pudo adquirir el lock en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "1")
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "LOCK_TIMEOUT",
                        "La operación se está procesando; reintentá en unos segundos", List.of(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrió un error inesperado", List.of(), request);
    }

    private Map<String, Object> fieldError(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "valor inválido" : error.getDefaultMessage());
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String code, String detail,
                                                 List<Map<String, Object>> errors, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(status, code, detail, errors, request));
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail,
                                  List<Map<String, Object>> errors, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://ticketya.local/errors/" + code.toLowerCase().replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("errors", errors == null ? List.of() : errors);
        return problem;
    }
}
