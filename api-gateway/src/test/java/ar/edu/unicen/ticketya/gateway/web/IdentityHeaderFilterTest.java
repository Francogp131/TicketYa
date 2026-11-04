package ar.edu.unicen.ticketya.gateway.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifica el contrato de identidad y errores del gateway sin depender de los servicios.
 * Las peticiones rechazadas por el filtro nunca se rutean, así que no hace falta levantarlos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityHeaderFilterTest {

    private static final String ADMIN = "11111111-1111-4111-8111-111111111111";
    private static final String CLIENT = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private WebTestClient client;

    @Test
    void sinIdentidadDevuelve401EnFormatoRfc7807() {
        client.post().uri("/api/orders")
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("reservaId", "00000000-0000-4000-8000-000000000000",
                        "medioPago", "TARJETA"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.instance").isEqualTo("/api/orders")
                .jsonPath("$.errors").isArray();
    }

    @Test
    void unUuidInvalidoDevuelve401() {
        client.get().uri("/api/orders/00000000-0000-4000-8000-000000000000")
                .header("X-User-Id", "no-es-uuid")
                .header("X-User-Role", "CLIENT")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void unRolDesconocidoDevuelve401() {
        client.get().uri("/api/orders/00000000-0000-4000-8000-000000000000")
                .header("X-User-Id", CLIENT)
                .header("X-User-Role", "SUPERUSER")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void unClienteNoPuedeCrearLugares() {
        client.post().uri("/api/catalog/venues")
                .contentType(APPLICATION_JSON)
                .header("X-User-Id", CLIENT)
                .header("X-User-Role", "CLIENT")
                .bodyValue(Map.of("nombre", "Estadio", "direccion", "Calle 1", "ciudad", "Tandil",
                        "latitud", -37.3, "longitud", -59.1))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.instance").isEqualTo("/api/catalog/venues");
    }

    @Test
    void unOrganizadorNoPuedeCrearLugares() {
        client.post().uri("/api/catalog/venues")
                .contentType(APPLICATION_JSON)
                .header("X-User-Id", ADMIN)
                .header("X-User-Role", "ORGANIZER")
                .bodyValue(Map.of("nombre", "Estadio", "direccion", "Calle 1", "ciudad", "Tandil",
                        "latitud", -37.3, "longitud", -59.1))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }
}
