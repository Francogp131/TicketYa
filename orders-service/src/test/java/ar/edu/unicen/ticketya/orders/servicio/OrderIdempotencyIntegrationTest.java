package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import ar.edu.unicen.ticketya.orders.repositorio.IdempotencyKeyRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tarea 4.6: con PostgreSQL real, dos reintentos concurrentes con la misma
 * Idempotency-Key no pueden crear dos órdenes.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderIdempotencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private InventoryClient inventoryClient;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private final UUID clienteId = UUID.randomUUID();
    private final UUID reservaId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
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
                .thenReturn(new PaymentGateway.PaymentResult(EstadoPago.APROBADO, "mock"));
    }

    @Test
    void dosReintentosConcurrentesConLaMismaClaveCreanUnaSolaOrden() throws Exception {
        CrearOrdenRequest request = new CrearOrdenRequest(reservaId, MedioPago.TARJETA, "APROBADO");
        String clave = "compra-concurrente-0001";
        int hilos = 2;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(hilos);
        AtomicInteger exitos = new AtomicInteger();
        List<String> errores = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                try {
                    inicio.await();
                    orderService.crear(request, clienteId, clave);
                    exitos.incrementAndGet();
                } catch (ApiException ex) {
                    errores.add(ex.getCode());
                } catch (Exception ex) {
                    errores.add(ex.getClass().getSimpleName());
                } finally {
                    fin.countDown();
                }
            });
        }
        inicio.countDown();
        assertThat(fin.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(ordenRepository.count()).isEqualTo(1);
        if (exitos.get() == 1) {
            assertThat(errores).contains("IDEMPOTENCY_IN_PROGRESS");
        } else {
            assertThat(exitos.get()).isEqualTo(2);
        }
        assertThat(idempotencyKeyRepository.findById(clave)).isPresent();
    }

    @Test
    void unReintentoSecuencialDevuelveLaMismaOrden() {
        CrearOrdenRequest request = new CrearOrdenRequest(reservaId, MedioPago.TARJETA, "APROBADO");
        OrderResponse primera = orderService.crear(request, clienteId, "compra-secuencial-0001");
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        OrderResponse segunda = orderService.crear(request, clienteId, "compra-secuencial-0001");

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(ordenRepository.count()).isEqualTo(1);
        assertThat(segunda.entradas()).isEqualTo(primera.entradas());
        assertThat(segunda.entradas()).hasSize(1);
    }
}
