package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reglas de reserva contra PostgreSQL real: sector cruzado y evento con fecha pasada.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ReservaReglasPostgresTest {

    private static final UUID EVENTO_FECHA = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID CAMPO = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    private static final UUID EVENTO_MISMATCH = UUID.fromString("d0000000-0000-4000-8000-000000000002");
    private static final UUID PULLMAN = UUID.fromString("b0000000-0000-4000-8000-000000000003");
    private static final UUID PLATEA_ALTA = UUID.fromString("b0000000-0000-4000-8000-000000000004");
    private static final UUID ASIENTO_PULLMAN = UUID.fromString("c0000000-0000-4000-8000-030101000000");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ReservaService reservaService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void preparar() {
        restaurar();
    }

    @AfterEach
    void restaurar() {
        jdbcTemplate.update("UPDATE evento_inventario SET fecha = '2027-03-14T21:00:00-03:00' WHERE evento_id = ?",
                EVENTO_FECHA);
        jdbcTemplate.update("UPDATE evento_inventario SET fecha = '2027-04-18T20:30:00-03:00' WHERE evento_id = ?",
                EVENTO_MISMATCH);
        jdbcTemplate.update("""
                UPDATE asiento_inventario
                   SET estado = 'LIBRE', reserva_id = NULL, expires_at = NULL
                 WHERE evento_id = ? AND asiento_id = ?
                """, EVENTO_MISMATCH, ASIENTO_PULLMAN);
    }

    @Test
    void unAsientoDePullmanDeclaradoEnPlateaAltaDevuelve400YNoReservaNada() {
        ApiException error = catchThrowableOfType(() -> reservaService.reservar(
                new ReservaRequest(EVENTO_MISMATCH, List.of(new ReservaRequest.Item(
                        PLATEA_ALTA, List.of(ASIENTO_PULLMAN), null))),
                UUID.randomUUID()), ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(400);
        assertThat(error.getCode()).isEqualTo("SECTOR_MISMATCH");
        assertThat(estadoAsiento()).isEqualTo("LIBRE");
    }

    @Test
    void unEventoConFechaPasadaDevuelve409YNoReservaNada() {
        jdbcTemplate.update("UPDATE evento_inventario SET fecha = now() - interval '1 day' WHERE evento_id = ?",
                EVENTO_FECHA);
        int antes = disponibles();

        ApiException error = catchThrowableOfType(() -> reservaService.reservar(
                new ReservaRequest(EVENTO_FECHA, List.of(new ReservaRequest.Item(CAMPO, null, 1))),
                UUID.randomUUID()), ApiException.class);

        assertThat(error.getStatus().value()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo("EVENT_FINISHED");
        assertThat(disponibles()).isEqualTo(antes);
    }

    private String estadoAsiento() {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM asiento_inventario WHERE evento_id = ? AND asiento_id = ?",
                String.class, EVENTO_MISMATCH, ASIENTO_PULLMAN);
    }

    private int disponibles() {
        return jdbcTemplate.queryForObject(
                "SELECT disponibles FROM sector_inventario WHERE evento_id = ? AND sector_id = ?",
                Integer.class, EVENTO_FECHA, CAMPO);
    }
}
