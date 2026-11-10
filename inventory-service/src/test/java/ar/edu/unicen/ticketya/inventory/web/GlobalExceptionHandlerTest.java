package ar.edu.unicen.ticketya.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unLockTimeoutDevuelve503ConRetryAfter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/inventory/reservations");

        ResponseEntity<ProblemDetail> response = handler.handleLockTimeout(
                new CannotAcquireLockException("canceling statement due to lock timeout"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "LOCK_TIMEOUT");
    }
}
