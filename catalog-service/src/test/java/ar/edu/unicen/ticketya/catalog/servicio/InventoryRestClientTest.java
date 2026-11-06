package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class InventoryRestClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void levantarServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void bajarServidor() {
        server.stop(0);
    }

    @Test
    void publicaElSnapshotYReintentaAnteErrores5xx() {
        AtomicInteger intentos = new AtomicInteger();
        List<String> cuerpos = new CopyOnWriteArrayList<>();
        server.createContext("/internal/events/" + SNAPSHOT.eventoId() + "/inventory", exchange -> {
            cuerpos.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int intento = intentos.incrementAndGet();
            responder(exchange, intento < 3 ? 500 : 200, "{}");
        });

        cliente().publicarInventario(SNAPSHOT);

        assertThat(intentos).hasValue(3);
        assertThat(cuerpos).hasSize(3).allSatisfy(cuerpo -> {
            assertThat(cuerpo).contains(SNAPSHOT.eventoId().toString());
            assertThat(cuerpo).contains("\"version\":1");
        });
    }

    @Test
    void trasTresIntentosPropagaElErrorDeInventory() {
        AtomicInteger intentos = new AtomicInteger();
        server.createContext("/internal/events/" + SNAPSHOT.eventoId() + "/inventory", exchange -> {
            intentos.incrementAndGet();
            responder(exchange, 503, "{}");
        });

        assertThatThrownBy(() -> cliente().publicarInventario(SNAPSHOT))
                .isInstanceOf(RuntimeException.class);
        assertThat(intentos).hasValue(InventoryRestClient.MAX_INTENTOS);
    }

    @Test
    void noReintentaErrores4xx() {
        AtomicInteger intentos = new AtomicInteger();
        server.createContext("/internal/events/" + SNAPSHOT.eventoId() + "/inventory", exchange -> {
            intentos.incrementAndGet();
            responder(exchange, 422, "{\"code\":\"EVENT_INCOMPLETE\"}");
        });

        assertThatThrownBy(() -> cliente().publicarInventario(SNAPSHOT))
                .isInstanceOf(RuntimeException.class);
        assertThat(intentos).hasValue(1);
    }

    @Test
    void consultaLaDisponibilidadPorSector() {
        UUID sectorId = UUID.randomUUID();
        server.createContext("/api/inventory/events/" + SNAPSHOT.eventoId() + "/availability", exchange ->
                responder(exchange, 200, """
                        {
                          "eventoId": "%s",
                          "estado": "PUBLICADO",
                          "sectores": [
                            {"sectorId": "%s", "nombre": "Platea", "tipo": "NUMERADO",
                             "precio": 15000.00, "capacidad": 20, "disponibles": 18}
                          ]
                        }
                        """.formatted(SNAPSHOT.eventoId(), sectorId)));

        Map<UUID, Integer> disponibilidad = cliente().obtenerDisponibilidad(SNAPSHOT.eventoId());

        assertThat(disponibilidad).containsExactly(Map.entry(sectorId, 18));
    }

    private InventoryRestClient cliente() {
        return new InventoryRestClient(RestClient.builder(), baseUrl, 1000, 1000);
    }

    private void responder(HttpExchange exchange, int status, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream salida = exchange.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static final InventorySnapshot SNAPSHOT = new InventorySnapshot(
            UUID.randomUUID(),
            "Festival",
            OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
            4,
            1L,
            List.of(new InventorySnapshot.SectorSnapshot(
                    UUID.randomUUID(),
                    "Platea",
                    "NUMERADO",
                    new BigDecimal("15000.00"),
                    20,
                    2,
                    List.of(new InventorySnapshot.AsientoSnapshot(UUID.randomUUID(), 1, 1)))));
}
