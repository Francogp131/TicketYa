package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private OrdenRepository ordenRepository;
    @Mock
    private OrdenItemRepository ordenItemRepository;
    @Mock
    private EntradaRepository entradaRepository;
    @Mock
    private InventoryClient inventoryClient;

    private OrderService orderService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZONA);
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(ordenRepository, ordenItemRepository, entradaRepository,
                inventoryClient, clock);
    }

    @Test
    void crearOrdenDesdeReservaCalculaElTotalEnElServidor() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);
        when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> {
            Orden orden = invocation.getArgument(0);
            orden.setId(UUID.randomUUID());
            return orden;
        });

        OrderResponse response = orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, "APROBADO"),
                clienteId, "compra-0001");

        assertThat(response.estado()).isEqualTo(EstadoOrden.PENDIENTE_PAGO);
        assertThat(response.total()).isEqualByComparingTo("30000.00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.eventoNombre()).isEqualTo("Recital");
        verify(ordenItemRepository).saveAll(any());
    }

    @Test
    void unaReservaAjenaDevuelve403() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, null),
                UUID.randomUUID(), "compra-0002"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("NOT_RESERVATION_OWNER");
                });
    }

    @Test
    void unaReservaInexistenteDevuelve404() {
        UUID reservaId = UUID.randomUUID();
        when(inventoryClient.obtenerReserva(reservaId)).thenThrow(new RestClientResponseException(
                "not found", HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null));

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reservaId, MedioPago.TARJETA, null),
                clienteId, "compra-0003"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("RESERVATION_NOT_FOUND");
                });
    }

    @Test
    void unaReservaVencidaDevuelve410() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T11:59:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, null),
                clienteId, "compra-0004"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(410);
                    assertThat(ex.getCode()).isEqualTo("RESERVATION_EXPIRED");
                });
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void siInventoryNoRespondeDevuelve502() {
        UUID reservaId = UUID.randomUUID();
        when(inventoryClient.obtenerReserva(reservaId)).thenThrow(new RuntimeException("caído"));

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reservaId, MedioPago.TARJETA, null),
                clienteId, "compra-0005"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(502);
                    assertThat(ex.getCode()).isEqualTo("INVENTORY_UNAVAILABLE");
                });
    }

    @Test
    void sinIdempotencyKeyDevuelve400AntesDeConsultarInventory() {
        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(UUID.randomUUID(), MedioPago.TARJETA, null),
                clienteId, "corta"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
        verify(inventoryClient, never()).obtenerReserva(any());
    }

    private ReservationDto reserva(String estado, OffsetDateTime expiresAt) {
        UUID eventoId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        return new ReservationDto(
                UUID.randomUUID(),
                eventoId,
                "Recital",
                OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                clienteId,
                estado,
                expiresAt,
                List.of(
                        new ReservationDto.Item(sectorId, UUID.randomUUID(), 1,
                                new BigDecimal("15000.00"), new BigDecimal("15000.00")),
                        new ReservationDto.Item(sectorId, UUID.randomUUID(), 1,
                                new BigDecimal("15000.00"), new BigDecimal("15000.00"))),
                new BigDecimal("30000.00"));
    }
}
