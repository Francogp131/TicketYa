package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import java.util.UUID;
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
class VencimientoPostgresTest {

    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID PLATEA = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID CAMPO = UUID.fromString("b0000000-0000-4000-8000-000000000002");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private VencimientoService vencimientoService;
    @Autowired
    private ReservaService reservaService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unaReservaVencidaLiberaElAsiento() {
        UUID clienteId = UUID.randomUUID();
        UUID asientoId = asientoLibre();
        UUID reservaId = reservarAsiento(clienteId, asientoId, true);

        vencimientoService.liberarVencidas();

        assertThat(estadoReserva(reservaId)).isEqualTo("VENCIDA");
        assertThat(estadoAsiento(asientoId)).isEqualTo("LIBRE");
        assertThat(reservaDelAsiento(asientoId)).isNull();
    }

    @Test
    void unaReservaVencidaReponeLaZonaYNoLaReponeDosVeces() {
        int antes = disponibles();
        UUID reservaId = reservarZona(2, true);
        assertThat(disponibles()).isEqualTo(antes - 2);

        vencimientoService.liberarVencidas();
        vencimientoService.liberarVencidas();

        assertThat(estadoReserva(reservaId)).isEqualTo("VENCIDA");
        assertThat(disponibles()).isEqualTo(antes);
    }

    @Test
    void unaReservaVigenteNoSeLibera() {
        UUID clienteId = UUID.randomUUID();
        UUID asientoId = asientoLibre();
        UUID reservaId = reservarAsiento(clienteId, asientoId, false);

        vencimientoService.liberarVencidas();

        assertThat(estadoReserva(reservaId)).isEqualTo("VIGENTE");
        assertThat(estadoAsiento(asientoId)).isEqualTo("RESERVADO");
    }

    @Test
    void consultarUnaReservaVencidaLaLiberaYRespondeVencida() {
        UUID clienteId = UUID.randomUUID();
        UUID asientoId = asientoLibre();
        UUID reservaId = reservarAsiento(clienteId, asientoId, true);

        var respuesta = reservaService.obtener(reservaId, clienteId);

        assertThat(respuesta.estado().name()).isEqualTo("VENCIDA");
        assertThat(estadoAsiento(asientoId)).isEqualTo("LIBRE");
    }

    @Test
    void pagarUnaReservaVencidaResponde410YReponeLaZona() {
        int antes = disponibles();
        UUID reservaId = reservarZona(2, true);

        ApiException error = catchThrowableOfType(
                () -> reservaService.validarParaPago(reservaId), ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("RESERVATION_EXPIRED");
        assertThat(disponibles()).isEqualTo(antes);
        assertThat(estadoReserva(reservaId)).isEqualTo("VENCIDA");
    }

    private UUID reservarAsiento(UUID clienteId, UUID asientoId, boolean vencida) {
        UUID reservaId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reserva (id, evento_id, cliente_id, estado, expires_at)
                VALUES (?, ?, ?, 'VIGENTE', now() + (? || ' minutes')::interval)
                """, reservaId, EVENTO, clienteId, vencida ? "-1" : "10");
        jdbcTemplate.update("""
                UPDATE asiento_inventario
                   SET estado = 'RESERVADO', reserva_id = ?, expires_at = now() + (? || ' minutes')::interval
                 WHERE evento_id = ? AND asiento_id = ?
                """, reservaId, vencida ? "-1" : "10", EVENTO, asientoId);
        jdbcTemplate.update("""
                INSERT INTO reserva_item (reserva_id, sector_id, asiento_id, cantidad)
                VALUES (?, ?, ?, 1)
                """, reservaId, PLATEA, asientoId);
        return reservaId;
    }

    private UUID reservarZona(int cantidad, boolean vencida) {
        UUID reservaId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reserva (id, evento_id, cliente_id, estado, expires_at)
                VALUES (?, ?, ?, 'VIGENTE', now() + (? || ' minutes')::interval)
                """, reservaId, EVENTO, UUID.randomUUID(), vencida ? "-1" : "10");
        jdbcTemplate.update("""
                UPDATE sector_inventario
                   SET disponibles = disponibles - ?
                 WHERE evento_id = ? AND sector_id = ?
                """, cantidad, EVENTO, CAMPO);
        jdbcTemplate.update("""
                INSERT INTO reserva_item (reserva_id, sector_id, asiento_id, cantidad)
                VALUES (?, ?, NULL, ?)
                """, reservaId, CAMPO, cantidad);
        return reservaId;
    }

    private UUID asientoLibre() {
        return jdbcTemplate.queryForObject("""
                SELECT asiento_id FROM asiento_inventario
                 WHERE evento_id = ? AND sector_id = ? AND estado = 'LIBRE'
                 ORDER BY asiento_id
                 LIMIT 1
                """, UUID.class, EVENTO, PLATEA);
    }

    private String estadoReserva(UUID reservaId) {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM reserva WHERE id = ?", String.class, reservaId);
    }

    private String estadoAsiento(UUID asientoId) {
        return jdbcTemplate.queryForObject("""
                SELECT estado FROM asiento_inventario
                 WHERE evento_id = ? AND asiento_id = ?
                """, String.class, EVENTO, asientoId);
    }

    private UUID reservaDelAsiento(UUID asientoId) {
        return jdbcTemplate.queryForObject("""
                SELECT reserva_id FROM asiento_inventario
                 WHERE evento_id = ? AND asiento_id = ?
                """, UUID.class, EVENTO, asientoId);
    }

    private int disponibles() {
        Integer valor = jdbcTemplate.queryForObject("""
                SELECT disponibles FROM sector_inventario
                 WHERE evento_id = ? AND sector_id = ?
                """, Integer.class, EVENTO, CAMPO);
        return valor == null ? 0 : valor;
    }
}
