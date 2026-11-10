package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReturnRequest;
import java.util.List;
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
class ConfirmacionPostgresTest {

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
    private ConfirmacionService confirmacionService;
    @Autowired
    private ReservaService reservaService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void confirmarDosVecesDejaElAsientoVendidoUnaSolaVez() {
        UUID clienteId = UUID.randomUUID();
        UUID asientoId = asientoLibre();
        UUID reservaId = reservarAsiento(clienteId, asientoId, false);

        var primera = confirmacionService.confirmar(reservaId);
        var segunda = confirmacionService.confirmar(reservaId);

        assertThat(primera.estado().name()).isEqualTo("CONFIRMADA");
        assertThat(segunda.estado().name()).isEqualTo("CONFIRMADA");
        assertThat(estadoAsiento(asientoId)).isEqualTo("VENDIDO");
        assertThat(vendidas(clienteId)).isEqualTo(1);
    }

    @Test
    void confirmarUnaReservaVencidaResponde410YLiberaElAsiento() {
        UUID clienteId = UUID.randomUUID();
        UUID asientoId = asientoLibre();
        UUID reservaId = reservarAsiento(clienteId, asientoId, true);

        ApiException error = catchThrowableOfType(
                () -> confirmacionService.confirmar(reservaId), ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("RESERVATION_EXPIRED");
        assertThat(estadoReserva(reservaId)).isEqualTo("VENCIDA");
        assertThat(estadoAsiento(asientoId)).isEqualTo("LIBRE");
    }

    @Test
    void liberarDosVecesReponeLaZonaUnaSolaVez() {
        int antes = disponibles();
        UUID reservaId = reservarZona(2, false);
        assertThat(disponibles()).isEqualTo(antes - 2);

        confirmacionService.liberar(reservaId);
        confirmacionService.liberar(reservaId);

        assertThat(estadoReserva(reservaId)).isEqualTo("LIBERADA");
        assertThat(disponibles()).isEqualTo(antes);
    }

    @Test
    void devolverUnAsientoVendidoLoLiberaUnaSolaVez() {
        UUID clienteId = UUID.randomUUID();
        UUID asientoId = asientoLibre();
        UUID reservaId = reservarAsiento(clienteId, asientoId, false);
        confirmacionService.confirmar(reservaId);

        ReturnRequest devolucion = new ReturnRequest(EVENTO, List.of(new ReturnRequest.Item(PLATEA, asientoId, 1)), null);
        confirmacionService.devolver("asiento-" + asientoId, devolucion);
        confirmacionService.devolver("asiento-" + asientoId, devolucion);

        assertThat(estadoAsiento(asientoId)).isEqualTo("LIBRE");
        assertThat(vendidas(clienteId)).isZero();
    }

    @Test
    void devolverUnaZonaLaReponeUnaSolaVez() {
        int antes = disponibles();
        UUID reservaId = reservarZona(2, false);
        confirmacionService.confirmar(reservaId);
        assertThat(disponibles()).isEqualTo(antes - 2);

        ReturnRequest devolucion = new ReturnRequest(EVENTO, List.of(new ReturnRequest.Item(CAMPO, null, 2)),
                UUID.randomUUID());
        confirmacionService.devolver("zona-" + antes, devolucion);
        confirmacionService.devolver("zona-" + antes, devolucion);

        assertThat(disponibles()).isEqualTo(antes);
    }

    @Test
    void devolverUnaZonaSinClienteIdDevuelve400() {
        int antes = disponibles();
        UUID reservaId = reservarZona(2, false);
        confirmacionService.confirmar(reservaId);

        ApiException error = catchThrowableOfType(
                () -> confirmacionService.devolver("zona-sin-cliente",
                        new ReturnRequest(EVENTO, List.of(new ReturnRequest.Item(CAMPO, null, 1)), null)),
                ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(400);
        assertThat(error.getCode()).isEqualTo("CLIENT_ID_REQUIRED");
        assertThat(disponibles()).isEqualTo(antes - 2);
    }

    @Test
    void devolverUnaZonaLiberaElCupoYPermiteReservarDeNuevo() {
        UUID clienteId = UUID.randomUUID();
        var compra = reservaService.reservar(
                new ReservaRequest(EVENTO, List.of(new ReservaRequest.Item(CAMPO, null, 4))), clienteId);
        confirmacionService.confirmar(compra.id());
        assertThat(vendidas(clienteId)).isEqualTo(4);

        confirmacionService.devolver("cupo-" + clienteId,
                new ReturnRequest(EVENTO, List.of(new ReturnRequest.Item(CAMPO, null, 1)), clienteId));

        assertThat(vendidas(clienteId)).isEqualTo(3);
        var otra = reservaService.reservar(
                new ReservaRequest(EVENTO, List.of(new ReservaRequest.Item(CAMPO, null, 1))), clienteId);
        assertThat(otra.estado().name()).isEqualTo("VIGENTE");
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

    private int vendidas(UUID clienteId) {
        Integer valor = jdbcTemplate.queryForObject("""
                SELECT entradas_vendidas FROM cliente_evento
                 WHERE evento_id = ? AND cliente_id = ?
                """, Integer.class, EVENTO, clienteId);
        return valor == null ? 0 : valor;
    }

    private int disponibles() {
        Integer valor = jdbcTemplate.queryForObject("""
                SELECT disponibles FROM sector_inventario
                 WHERE evento_id = ? AND sector_id = ?
                """, Integer.class, EVENTO, CAMPO);
        return valor == null ? 0 : valor;
    }
}
