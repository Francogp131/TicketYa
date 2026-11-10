package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba el fallo real de emisión: el repositorio de entradas falla dentro de
 * TicketService.emitir y la compensación debe persistirse igual.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderTransactionEmissionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private ReintegroRepository reintegroRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private InventoryClient inventoryClient;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @MockitoBean
    private EntradaRepository entradaRepository;

    private final UUID clienteId = UUID.randomUUID();
    private final UUID reservaId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        jdbcTemplate.update("DELETE FROM reintegro");
        jdbcTemplate.update("DELETE FROM devolucion");
        jdbcTemplate.update("DELETE FROM entrada");
        jdbcTemplate.update("DELETE FROM pago");
        jdbcTemplate.update("DELETE FROM orden_item");
        jdbcTemplate.update("DELETE FROM orden");
        jdbcTemplate.update("DELETE FROM idempotency_key");

        ReservationDto reserva = new ReservationDto(reservaId, UUID.randomUUID(), "Recital",
                OffsetDateTime.parse("2027-01-15T21:00:00-03:00"), clienteId, "VIGENTE",
                OffsetDateTime.now().plusMinutes(10),
                List.of(new ReservationDto.Item(UUID.randomUUID(), UUID.randomUUID(), 1,
                        new BigDecimal("15000.00"), new BigDecimal("15000.00"))),
                new BigDecimal("15000.00"));
        when(inventoryClient.obtenerReserva(any())).thenReturn(reserva);
        when(paymentGateway.cobrar(any(), any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(EstadoPago.APROBADO, "mock-ok"));
        when(entradaRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("fallo de emisión"));
    }

    @Test
    void unFalloRealDeEmisionPersisteLaCompensacion() {
        CrearOrdenRequest request = new CrearOrdenRequest(reservaId, MedioPago.TARJETA, "APROBADO");

        var response = orderService.crear(request, clienteId, "tx-emision-real-01");

        assertThat(response.estado()).isEqualTo(EstadoOrden.REINTEGRO_PENDIENTE);
        assertThat(ordenRepository.count()).isEqualTo(1);
        Orden orden = ordenRepository.findAll().get(0);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.REINTEGRO_PENDIENTE);
        assertThat(reintegroRepository.findAll()).isNotEmpty();
    }
}
