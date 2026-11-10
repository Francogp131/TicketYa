package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
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
class DisponibilidadCatalogTest {

    private static final UUID EVENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID CAMPO = UUID.fromString("b0000000-0000-4000-8000-000000000002");

    private static final List<String> AVISOS = new CopyOnWriteArrayList<>();
    private static final HttpServer CATALOG = catalogo();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("catalog.url", () -> "http://localhost:" + CATALOG.getAddress().getPort());
    }

    @Autowired
    private ReservaService reservaService;
    @Autowired
    private ConfirmacionService confirmacionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterAll
    static void cerrarCatalogo() {
        CATALOG.stop(0);
    }

    @Test
    void elUltimoLugarAvisaAgotadoYLiberarloLoRevierte() throws Exception {
        AVISOS.clear();
        UUID reservaDeAsientos = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reserva (id, evento_id, cliente_id, estado, expires_at)
                VALUES (?, ?, ?, 'VIGENTE', now() + interval '10 minutes')
                """, reservaDeAsientos, EVENTO, UUID.randomUUID());
        jdbcTemplate.update("""
                UPDATE asiento_inventario
                   SET estado = 'RESERVADO', reserva_id = ?, expires_at = now() + interval '10 minutes'
                 WHERE evento_id = ?
                """, reservaDeAsientos, EVENTO);
        jdbcTemplate.update("""
                UPDATE sector_inventario SET disponibles = 1
                 WHERE evento_id = ? AND sector_id = ?
                """, EVENTO, CAMPO);

        var reserva = reservaService.reservar(
                new ReservaRequest(EVENTO, List.of(new ReservaRequest.Item(CAMPO, null, 1))),
                UUID.randomUUID());

        esperar(true);
        assertThat(estadoEvento()).isEqualTo("AGOTADO");

        confirmacionService.liberar(reserva.id());

        esperar(false);
        assertThat(estadoEvento()).isEqualTo("PUBLICADO");
    }

    private static void esperar(boolean agotado) throws InterruptedException {
        String esperado = agotado ? "\"agotado\":true" : "\"agotado\":false";
        for (int i = 0; i < 40 && AVISOS.stream().noneMatch(aviso -> aviso.contains(esperado)); i++) {
            Thread.sleep(50);
        }
        assertThat(AVISOS).anyMatch(aviso -> aviso.contains(esperado));
    }

    private String estadoEvento() {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM evento_inventario WHERE evento_id = ?", String.class, EVENTO);
    }

    private static HttpServer catalogo() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/internal/events/", exchange -> {
                AVISOS.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
