package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ar.edu.unicen.ticketya.inventory.repositorio.SectorInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Testcontainers
class LimitePorClientePostgresTest {

    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID ZONA = UUID.fromString("b0000000-0000-4000-8000-000000000002");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ReservaService reservaService;
    @Autowired
    private SectorInventarioRepository sectorRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void conTresVendidasYLimiteCuatroReservarDosResponde422YNoReserva() {
        UUID clienteId = UUID.randomUUID();
        vender(clienteId, 3);
        int antes = disponibles();

        ApiException error = catchThrowableOfType(
                () -> reservaService.reservar(pedido(2), clienteId), ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(422);
        assertThat(error.getCode()).isEqualTo("CLIENT_LIMIT_EXCEEDED");
        assertThat(disponibles()).isEqualTo(antes);
        assertThat(reservasDe(clienteId)).isZero();
    }

    @Test
    void conUnaVendidaYLimiteCuatroReservarDosCreaLaReserva() {
        UUID clienteId = UUID.randomUUID();
        vender(clienteId, 1);
        int antes = disponibles();

        var respuesta = reservaService.reservar(pedido(2), clienteId);

        assertThat(respuesta.estado().name()).isEqualTo("VIGENTE");
        assertThat(respuesta.items()).singleElement().satisfies(item -> assertThat(item.cantidad()).isEqualTo(2));
        assertThat(disponibles()).isEqualTo(antes - 2);
        assertThat(cantidadVigente(clienteId)).isEqualTo(2);
    }

    @Test
    void dosReservasConcurrentesDelMismoClienteNoSuperanElLimite() throws Exception {
        UUID clienteId = UUID.randomUUID();
        int antes = disponibles();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        reservaService.reservar(pedido(3), clienteId);
                        return 201;
                    } catch (ApiException ex) {
                        return ex.getStatus().value();
                    }
                }));
            }
            List<Integer> codigos = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                codigos.add(futuro.get(20, TimeUnit.SECONDS));
            }
            assertThat(codigos).containsExactlyInAnyOrder(201, 422);
            assertThat(disponibles()).isEqualTo(antes - 3);
            assertThat(cantidadVigente(clienteId)).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void laReservaEsperaElLockDelMismoCliente() throws Exception {
        UUID clienteId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch adquirido = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
            jdbcTemplate.query(LimitePorCliente.LOCK, rs -> null, EVENTO.toString(), clienteId.toString());
            adquirido.countDown();
            try {
                if (!soltar.await(15, TimeUnit.SECONDS)) {
                    throw new TimeoutException("el test no soltó el lock");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            } catch (TimeoutException ex) {
                throw new IllegalStateException(ex);
            }
        }));
        Future<?> reserva = null;
        try {
            assertThat(adquirido.await(10, TimeUnit.SECONDS)).isTrue();
            reserva = pool.submit(() -> reservaService.reservar(pedido(1), clienteId));
            boolean bloqueada = false;
            for (int i = 0; i < 50 && !reserva.isDone(); i++) {
                Long esperando = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM pg_stat_activity
                         WHERE pid <> pg_backend_pid()
                           AND state = 'active'
                           AND wait_event_type = 'Lock'
                           AND query LIKE '%pg_advisory_xact_lock%'
                        """, Long.class);
                if (esperando != null && esperando >= 1) {
                    bloqueada = true;
                    break;
                }
                Thread.sleep(100);
            }
            assertThat(bloqueada).isTrue();
            assertThat(reserva.isDone()).isFalse();
        } finally {
            soltar.countDown();
            if (reserva != null) {
                assertThat(reserva.get(15, TimeUnit.SECONDS)).isNotNull();
            }
            holder.get(15, TimeUnit.SECONDS);
            pool.shutdownNow();
        }
    }

    private ReservaRequest pedido(int cantidad) {
        return new ReservaRequest(EVENTO, List.of(new ReservaRequest.Item(ZONA, null, cantidad)));
    }

    private void vender(UUID clienteId, int entradas) {
        jdbcTemplate.update("""
                INSERT INTO cliente_evento (evento_id, cliente_id, entradas_vendidas, updated_at)
                VALUES (?, ?, ?, now())
                """, EVENTO, clienteId, entradas);
    }

    private int disponibles() {
        return sectorRepository.findByEventoIdAndSectorId(EVENTO, ZONA).orElseThrow().getDisponibles();
    }

    private long reservasDe(UUID clienteId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reserva WHERE cliente_id = ?", Long.class, clienteId);
        return total == null ? 0 : total;
    }

    private int cantidadVigente(UUID clienteId) {
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(ri.cantidad), 0)::int
                  FROM reserva r
                  JOIN reserva_item ri ON ri.reserva_id = r.id
                 WHERE r.cliente_id = ? AND r.estado = 'VIGENTE'
                """, Integer.class, clienteId);
        return total == null ? 0 : total;
    }
}
