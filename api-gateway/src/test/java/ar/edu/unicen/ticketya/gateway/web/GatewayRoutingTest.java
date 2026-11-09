package ar.edu.unicen.ticketya.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifica que el gateway tenga las tres rutas y que aplique las reglas de rol
 * del contrato. Las peticiones rechazadas nunca llegan a los servicios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final String UUID_CUALQUIERA = "00000000-0000-4000-8000-000000000000";
    private static final String ORGANIZER = "22222222-2222-4222-8222-222222222222";
    private static final String CLIENT = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private WebTestClient client;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void lasTresRutasApuntanAlServicioCorrecto() {
        Map<String, URI> rutas = routeLocator.getRoutes()
                .collectMap(Route::getId, Route::getUri)
                .block();

        assertThat(rutas).containsKeys("catalog-service", "inventory-service", "orders-service");
        assertThat(rutas.get("catalog-service").getPort()).isEqualTo(8081);
        assertThat(rutas.get("inventory-service").getPort()).isEqualTo(8082);
        assertThat(rutas.get("orders-service").getPort()).isEqualTo(8083);
    }

    @Test
    void unClienteNoPuedeAprobarDevoluciones() {
        client.post().uri("/api/orders/returns/" + UUID_CUALQUIERA + "/approve")
                .header("X-User-Id", CLIENT)
                .header("X-User-Role", "CLIENT")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unOrganizadorNoPuedeCancelarEventos() {
        client.post().uri("/api/catalog/events/" + UUID_CUALQUIERA + "/cancel")
                .header("X-User-Id", ORGANIZER)
                .header("X-User-Role", "ORGANIZER")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void unClienteNoPuedePublicarEventos() {
        client.post().uri("/api/catalog/events/" + UUID_CUALQUIERA + "/publish")
                .header("X-User-Id", CLIENT)
                .header("X-User-Role", "CLIENT")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void reservarSinIdentidadDevuelve401() {
        client.post().uri("/api/inventory/reservations")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}