package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class OrdersRestClientTest {

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
    void cancelaElEventoConElContratoInterno() {
        UUID eventoId = UUID.randomUUID();
        CopyOnWriteArrayList<String> pedidos = new CopyOnWriteArrayList<>();
        server.createContext("/internal/events/" + eventoId + "/cancel", exchange -> {
            pedidos.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        cliente(1000).cancelarEvento(eventoId);

        assertThat(pedidos).containsExactly("POST /internal/events/" + eventoId + "/cancel");
    }

    @Test
    void propagaElErrorDeOrdersSinReintentar() {
        UUID eventoId = UUID.randomUUID();
        AtomicInteger intentos = new AtomicInteger();
        server.createContext("/internal/events/" + eventoId + "/cancel", exchange -> {
            intentos.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> cliente(1000).cancelarEvento(eventoId))
                .isInstanceOf(RestClientResponseException.class);
        assertThat(intentos).hasValue(1);
    }

    @Test
    void cortaLaEsperaSiOrdersNoResponde() {
        UUID eventoId = UUID.randomUUID();
        server.createContext("/internal/events/" + eventoId + "/cancel", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> cliente(100).cancelarEvento(eventoId))
                .isInstanceOf(ResourceAccessException.class);
    }

    private OrdersRestClient cliente(int readTimeoutMs) {
        return new OrdersRestClient(RestClient.builder(), baseUrl, 1000, readTimeoutMs);
    }
}
