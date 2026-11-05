package ar.edu.unicen.ticketya.orders.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio que se traduce al formato RFC 7807.
 * Los servicios lanzan ApiException con un código estable y el handler global arma la respuesta.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<Map<String, Object>> errors;
    private final Map<String, String> headers;

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, List.of(), Map.of());
    }

    public ApiException(HttpStatus status, String code, String detail, List<Map<String, Object>> errors) {
        this(status, code, detail, errors, Map.of());
    }

    public ApiException(HttpStatus status, String code, String detail,
                        List<Map<String, Object>> errors, Map<String, String> headers) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", detail);
    }

    public static ApiException forbidden(String code, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, code, detail);
    }

    public static ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException gone(String code, String detail) {
        return new ApiException(HttpStatus.GONE, code, detail);
    }

    public static ApiException unprocessable(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }

    public static ApiException unavailable(String code, String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, detail);
    }
}
