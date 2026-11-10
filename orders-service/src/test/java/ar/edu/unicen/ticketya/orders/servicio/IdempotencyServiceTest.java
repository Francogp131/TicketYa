package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.IdempotencyKey;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import ar.edu.unicen.ticketya.orders.repositorio.IdempotencyKeyRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        idempotencyService = new IdempotencyService(repository, objectMapper, jdbcTemplate);
    }

    @Test
    void unReintentoCompletadoDevuelveLaMismaRespuesta() throws Exception {
        OrderResponse original = respuesta();
        IdempotencyKey fila = new IdempotencyKey();
        fila.setKey("compra-0001");
        fila.setRequestHash("hash");
        fila.setEstado("COMPLETADA");
        fila.setResponseBody(objectMapper.writeValueAsString(original));
        when(repository.findById("compra-0001")).thenReturn(Optional.of(fila));

        OrderResponse recuperada = idempotencyService.resolverExistente("compra-0001", "hash");

        assertThat(recuperada).isEqualTo(original);
    }

    @Test
    void laMismaClaveConOtroCuerpoDevuelve422() {
        IdempotencyKey fila = new IdempotencyKey();
        fila.setKey("compra-0001");
        fila.setRequestHash("hash-original");
        when(repository.findById("compra-0001")).thenReturn(Optional.of(fila));

        assertThatThrownBy(() -> idempotencyService.resolverExistente("compra-0001", "hash-distinto"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                    assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
    }

    @Test
    void unaCompraEnProcesoDevuelve409ConRetryAfter() {
        IdempotencyKey fila = new IdempotencyKey();
        fila.setKey("compra-0001");
        fila.setRequestHash("hash");
        fila.setEstado("EN_PROCESO");
        fila.setExpiresAt(OffsetDateTime.now().plusHours(1));
        when(repository.findById("compra-0001")).thenReturn(Optional.of(fila));

        assertThatThrownBy(() -> idempotencyService.resolverExistente("compra-0001", "hash"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_IN_PROGRESS");
                    assertThat(ex.getHeaders()).containsEntry("Retry-After", "1");
                });
    }

    @Test
    void unaClaveVencidaSeTrataComoInexistente() {
        IdempotencyKey fila = new IdempotencyKey();
        fila.setKey("compra-0001");
        fila.setRequestHash("hash");
        fila.setEstado("COMPLETADA");
        fila.setExpiresAt(OffsetDateTime.now().minusHours(1));
        when(repository.findById("compra-0001")).thenReturn(Optional.of(fila));

        assertThatThrownBy(() -> idempotencyService.resolverExistente("compra-0001", "hash"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_EXPIRED");
                });
        verify(repository).delete(fila);
    }

    @Test
    void registrarGuardaLaClaveComoEnProceso() {
        idempotencyService.registrar("compra-0001", "hash", OffsetDateTime.parse("2026-11-03T12:00:00-03:00"));

        verify(jdbcTemplate).update(anyString(), eq("compra-0001"), eq("hash"), any());
    }

    @Test
    void elHashDependeDeTodoElCuerpo() {
        String primero = idempotencyService.hash("reserva-1", "TARJETA", "APROBADO");
        String igual = idempotencyService.hash("reserva-1", "TARJETA", "APROBADO");
        String distinto = idempotencyService.hash("reserva-1", "TARJETA", "RECHAZADO");

        assertThat(primero).isEqualTo(igual).hasSize(64);
        assertThat(primero).isNotEqualTo(distinto);
    }

    private OrderResponse respuesta() {
        return new OrderResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Recital", OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                EstadoOrden.PAGADA, MedioPago.TARJETA, new BigDecimal("15000.00"),
                List.of(), List.of(), OffsetDateTime.parse("2026-11-03T12:00:00-03:00"));
    }
}
