package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import ar.edu.unicen.ticketya.catalog.web.ApiException;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearSectorRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.SectorResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regresión de sectores contra PostgreSQL real: el duplicado se detecta con la
 * consulta y también queda cubierto por el UNIQUE (lugar_id, nombre) del schema.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class VenueSectorIntegrationTest {

    private static final UUID LUGAR = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private VenueService venueService;

    @Test
    void crearUnSectorConNombreExistenteDevuelve409() {
        assertThatThrownBy(() -> venueService.crearSector(LUGAR,
                new CrearSectorRequest("Platea", TipoSector.NUMERADO, null, 2, 2)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("SECTOR_ALREADY_EXISTS");
                });
    }

    @Test
    void crearUnSectorUnicoLoPersisteYNoPermiteRepetirlo() {
        String nombre = "Sector-" + UUID.randomUUID();

        SectorResponse creado = venueService.crearSector(LUGAR,
                new CrearSectorRequest(nombre, TipoSector.NO_NUMERADO, 50, null, null));

        assertThat(creado.nombre()).isEqualTo(nombre);
        assertThat(creado.capacidad()).isEqualTo(50);

        assertThatThrownBy(() -> venueService.crearSector(LUGAR,
                new CrearSectorRequest(nombre, TipoSector.NO_NUMERADO, 50, null, null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("SECTOR_ALREADY_EXISTS");
                });
    }
}
