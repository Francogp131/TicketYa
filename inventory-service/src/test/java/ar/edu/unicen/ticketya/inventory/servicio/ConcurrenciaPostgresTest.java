package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ConcurrenciaPostgresTest {

    private static final int HILOS_ASIENTO = 100;
    private static final int HILOS_ZONA = 20;

    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID PLATEA = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID CAMPO = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    private static final UUID ASIENTO = UUID.fromString("c0000000-0000-4000-8000-010101000000");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "40");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "60000");
    }

    @Autowired
    private ReservaService reservaService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cienHilosSobreElMismoAsientoDejanUnaSolaReserva() throws Exception {
        ReservaRequest pedido = new ReservaRequest(EVENTO, List.of(
                new ReservaRequest.Item(PLATEA, List.of(ASIENTO), null)));

        List<Resultado> resultados = enParalelo(HILOS_ASIENTO, () ->
                reservaService.reservar(pedido, UUID.randomUUID()));

        assertThat(otros(resultados)).isEmpty();
        assertThat(exitos(resultados)).isEqualTo(1);
        assertThat(conflictos(resultados, "SEAT_NOT_AVAILABLE")).isEqualTo(HILOS_ASIENTO - 1);
        assertThat(estadoAsiento()).isEqualTo("RESERVADO");
        assertThat(reservasDelAsiento()).isEqualTo(1);
    }

    @Test
    void variosHilosSobreElUltimoLugarDejanLaZonaEnCero() throws Exception {
        jdbcTemplate.update("""
                UPDATE sector_inventario
                   SET disponibles = 1
                 WHERE evento_id = ? AND sector_id = ?
                """, EVENTO, CAMPO);
        ReservaRequest pedido = new ReservaRequest(EVENTO, List.of(
                new ReservaRequest.Item(CAMPO, null, 1)));

        List<Resultado> resultados = enParalelo(HILOS_ZONA, () ->
                reservaService.reservar(pedido, UUID.randomUUID()));

        assertThat(otros(resultados)).isEmpty();
        assertThat(exitos(resultados)).isEqualTo(1);
        assertThat(conflictos(resultados, "INSUFFICIENT_AVAILABILITY")).isEqualTo(HILOS_ZONA - 1);
        assertThat(disponibles()).isZero();
        assertThat(entradasVigentesDeLaZona()).isEqualTo(1);
    }

    private List<Resultado> enParalelo(int hilos, RunnableReserva accion) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Resultado>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futuros.add(pool.submit(() -> {
                    listos.countDown();
                    salida.await();
                    try {
                        accion.reservar();
                        return Resultado.exitoso();
                    } catch (ApiException ex) {
                        return Resultado.error(ex);
                    } catch (RuntimeException ex) {
                        return Resultado.otro(ex);
                    }
                }));
            }
            assertThat(listos.await(30, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            List<Resultado> resultados = new ArrayList<>();
            for (Future<Resultado> futuro : futuros) {
                resultados.add(futuro.get(2, TimeUnit.MINUTES));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long exitos(List<Resultado> resultados) {
        return resultados.stream().filter(Resultado::exito).count();
    }

    private static long conflictos(List<Resultado> resultados, String codigo) {
        return resultados.stream()
                .filter(resultado -> resultado.error != null
                        && resultado.error.getStatus().value() == 409
                        && codigo.equals(resultado.error.getCode()))
                .count();
    }

    private static List<Throwable> otros(List<Resultado> resultados) {
        return resultados.stream()
                .map(Resultado::otro)
                .filter(otro -> otro != null)
                .toList();
    }

    private String estadoAsiento() {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM asiento_inventario WHERE asiento_id = ?", String.class, ASIENTO);
    }

    private int reservasDelAsiento() {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM reserva r
                  JOIN reserva_item ri ON ri.reserva_id = r.id
                 WHERE ri.asiento_id = ? AND r.estado = 'VIGENTE'
                """, Integer.class, ASIENTO);
        return cantidad == null ? 0 : cantidad;
    }

    private int disponibles() {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT disponibles FROM sector_inventario
                 WHERE evento_id = ? AND sector_id = ?
                """, Integer.class, EVENTO, CAMPO);
        return cantidad == null ? -1 : cantidad;
    }

    private int entradasVigentesDeLaZona() {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(ri.cantidad), 0)
                  FROM reserva_item ri
                  JOIN reserva r ON r.id = ri.reserva_id
                 WHERE ri.sector_id = ? AND ri.asiento_id IS NULL AND r.estado = 'VIGENTE'
                """, Integer.class, CAMPO);
        return cantidad == null ? 0 : cantidad;
    }

    @FunctionalInterface
    private interface RunnableReserva {
        void reservar();
    }

    private record Resultado(boolean exito, ApiException error, Throwable otro) {
        static Resultado exitoso() {
            return new Resultado(true, null, null);
        }

        static Resultado error(ApiException error) {
            return new Resultado(false, error, null);
        }

        static Resultado otro(Throwable otro) {
            return new Resultado(false, null, otro);
        }
    }
}
