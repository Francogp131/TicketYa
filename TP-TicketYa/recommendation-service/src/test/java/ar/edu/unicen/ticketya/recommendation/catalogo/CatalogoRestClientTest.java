package ar.edu.unicen.ticketya.recommendation.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CatalogoRestClientTest {

    private static final Instant DESDE = Instant.parse("2026-11-15T12:00:00Z");

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
    void devuelveCandidatosYFiltraPorCiudadYFecha() {
        List<String> queries = new CopyOnWriteArrayList<>();
        server.createContext("/internal/recommendations/candidates", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            responder(exchange, 200, """
                    [{"eventoId":"e1","nombre":"Festival","tipo":"FESTIVAL",
                      "artistas":["Los Piojos"],"ciudad":"Tandil",
                      "fechaEvento":"2027-03-15T00:00:00Z","estado":"PUBLICADO",
                      "altaDemanda":true}]
                    """);
        });

        List<EventoCandidato> candidatos = cliente().buscarCandidatos(preferencia(), DESDE);

        assertThat(candidatos).hasSize(1);
        assertThat(candidatos.get(0).eventoId()).isEqualTo("e1");
        assertThat(candidatos.get(0).altaDemanda()).isTrue();
        assertThat(queries).hasSize(1);
        assertThat(queries.get(0)).contains("ciudad=Tandil", "fechaDesde=2026-11-15T12:00:00Z");
    }

    @Test
    void reintentaErrores5xxYDosIntentos() {
        AtomicInteger intentos = new AtomicInteger();
        server.createContext("/internal/recommendations/candidates", exchange -> {
            intentos.incrementAndGet();
            responder(exchange, 503, "{}");
        });

        assertThatThrownBy(() -> cliente().buscarCandidatos(preferencia(), DESDE))
                .isInstanceOf(CatalogoUnavailableException.class);
        assertThat(intentos).hasValue(CatalogoRestClient.MAX_INTENTOS);
    }

    @Test
    void noReintentaUn404() {
        AtomicInteger intentos = new AtomicInteger();
        server.createContext("/internal/recommendations/candidates", exchange -> {
            intentos.incrementAndGet();
            responder(exchange, 404, "{}");
        });

        assertThatThrownBy(() -> cliente().buscarCandidatos(preferencia(), DESDE))
                .isInstanceOf(CatalogoUnavailableException.class);
        assertThat(intentos).hasValue(1);
    }

    private CatalogoRestClient cliente() {
        return new CatalogoRestClient(RestClient.builder(), baseUrl, 1000, 1000);
    }

    private Preferencia preferencia() {
        Preferencia preferencia = new Preferencia();
        preferencia.setClienteId("33333333-3333-4333-8333-333333333333");
        preferencia.setTipos(List.of("FESTIVAL"));
        preferencia.setArtistas(List.of("Los Piojos"));
        preferencia.setCiudad("Tandil");
        return preferencia;
    }

    private void responder(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
