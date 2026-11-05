package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tarea 3.7: no doble venta con PostgreSQL real. Cada hilo usa su propia
 * transacción; el objetivo es que a lo sumo uno gane el lugar disputado.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ReservaConcurrencyIntegrationTest {

    private static final int HILOS = 100;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID eventoId = UUID.randomUUID();
    private final UUID sectorId = UUID.randomUUID();
    private final UUID asientoId = UUID.randomUUID();

    @BeforeEach
    void limpiarBase() {
        jdbcTemplate.update("DELETE FROM devolucion_procesada");
        jdbcTemplate.update("DELETE FROM reserva_item");
        jdbcTemplate.update("DELETE FROM reserva");
        jdbcTemplate.update("DELETE FROM asiento_inventario");
        jdbcTemplate.update("DELETE FROM sector_inventario");
        jdbcTemplate.update("DELETE FROM cliente_evento");
        jdbcTemplate.update("DELETE FROM evento_inventario");
    }

    @Test
    void cienHilosSobreElMismoAsientoDejanExactamenteUnGanador() throws Exception {
        crearEvento(10);
        jdbcTemplate.update("""
                INSERT INTO sector_inventario (evento_id, sector_id, nombre, tipo, precio, capacidad, disponibles)
                VALUES (?, ?, 'Platea', 'NUMERADO', 15000.00, 1, NULL)
                """, eventoId, sectorId);
        jdbcTemplate.update("""
                INSERT INTO asiento_inventario (evento_id, sector_id, asiento_id, fila, numero, estado)
                VALUES (?, ?, ?, 1, 1, 'LIBRE')
                """, eventoId, sectorId, asientoId);

        Resultado resultado = disputar(HILOS, (cliente, indice) -> reservaService.reservar(
                new ReservaRequest(eventoId, List.of(
                        new ReservaRequest.Item(sectorId, List.of(asientoId), null))),
                cliente));

        assertThat(resultado.exitos()).isEqualTo(1);
        assertThat(resultado.errores())
                .allSatisfy(error -> assertThat(error).isEqualTo("SEAT_NOT_AVAILABLE"));
        assertThat(contar("select count(*) from reserva where evento_id = ?", eventoId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select estado from asiento_inventario where evento_id = ? and asiento_id = ?",
                String.class, eventoId, asientoId)).isEqualTo("RESERVADO");
    }

    @Test
    void variosHilosSobreElUltimoLugarDeUnaZonaDejanExactamenteUnGanador() throws Exception {
        crearEvento(10);
        jdbcTemplate.update("""
                INSERT INTO sector_inventario (evento_id, sector_id, nombre, tipo, precio, capacidad, disponibles)
                VALUES (?, ?, 'Campo', 'NO_NUMERADO', 8000.00, 1, 1)
                """, eventoId, sectorId);

        int hilos = 40;
        Resultado resultado = disputar(hilos, (cliente, indice) -> reservaService.reservar(
                new ReservaRequest(eventoId, List.of(
                        new ReservaRequest.Item(sectorId, null, 1))),
                cliente));

        assertThat(resultado.exitos()).isEqualTo(1);
        assertThat(resultado.errores())
                .allSatisfy(error -> assertThat(error).isEqualTo("INSUFFICIENT_AVAILABILITY"));
        assertThat(contar("select count(*) from reserva where evento_id = ?", eventoId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select disponibles from sector_inventario where evento_id = ? and sector_id = ?",
                Integer.class, eventoId, sectorId)).isZero();
    }

    private void crearEvento(int limite) {
        jdbcTemplate.update("""
                INSERT INTO evento_inventario (evento_id, nombre, fecha, estado, limite_por_cliente, version)
                VALUES (?, 'Evento de prueba', ?, 'PUBLICADO', ?, 1)
                """, eventoId, OffsetDateTime.parse("2027-06-01T21:00:00-03:00"), limite);
    }

    private Resultado disputar(int hilos, Intento intento) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(hilos);
        AtomicInteger exitos = new AtomicInteger();
        List<String> errores = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < hilos; i++) {
            int indice = i;
            UUID cliente = UUID.randomUUID();
            pool.submit(() -> {
                try {
                    inicio.await();
                    intento.ejecutar(cliente, indice);
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
        assertThat(fin.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        return new Resultado(exitos.get(), errores);
    }

    private long contar(String sql, UUID eventoId) {
        Long total = jdbcTemplate.queryForObject(sql, Long.class, eventoId);
        return total == null ? 0 : total;
    }

    @FunctionalInterface
    private interface Intento {
        ReservationResponse ejecutar(UUID cliente, int indice);
    }

    private record Resultado(int exitos, List<String> errores) {
    }
}
