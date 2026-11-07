package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoBusquedaResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PageResponse;
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
 * Regresión de la búsqueda contra PostgreSQL real: los filtros opcionales
 * deben funcionar con parámetros nulos y con texto en mayúsculas/minúsculas.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventoBusquedaIntegrationTest {

    private static final UUID FESTIVAL = UUID.fromString("d0000000-0000-4000-8000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private EventService eventService;

    @Test
    void buscarSinFiltrosDevuelveLosEventosPublicadosDelSeed() {
        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                null, null, null, null, null, null, "FECHA_ASC", 0, 20);

        assertThat(pagina.totalElements()).isGreaterThanOrEqualTo(3);
        assertThat(pagina.content()).extracting(EventoBusquedaResponse::id).contains(FESTIVAL);
    }

    @Test
    void buscarConTextoYMayusculasNormalizaParticipanteYCiudad() {
        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                null, null, "  Los Piojos ", " Tandil ", null, null, "PRECIO_ASC", 0, 20);

        assertThat(pagina.content()).extracting(EventoBusquedaResponse::id).containsExactly(FESTIVAL);
    }

    @Test
    void buscarCombinaTipoParticipanteYCiudad() {
        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                TipoEvento.FESTIVAL, null, "Los Piojos", "Tandil",
                null, null, "PRECIO_ASC", 0, 20);

        assertThat(pagina.content()).singleElement()
                .extracting(EventoBusquedaResponse::id)
                .isEqualTo(FESTIVAL);
    }
}
