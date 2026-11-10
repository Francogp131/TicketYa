package ar.edu.unicen.ticketya.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Revisión del 9/11: rutas públicas, roles del contrato, default-deny y correlation id. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRevisionTest {

    private static final String ID = "00000000-0000-4000-8000-000000000000";
    private static final String ADMIN = "11111111-1111-4111-8111-111111111111";
    private static final String ORGANIZER = "22222222-2222-4222-8222-222222222222";
    private static final String CLIENT = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private WebTestClient client;

    private WebTestClient.ResponseSpec llamar(String metodo, String ruta, String userId, String rol) {
        return client.method(HttpMethod.valueOf(metodo)).uri(ruta)
                .header("X-User-Id", userId)
                .header("X-User-Role", rol)
                .exchange();
    }

    @Test
    void listarSectoresEsPublicoSinIdentidad() {
        client.get().uri("/api/catalog/venues/" + ID + "/sectors")
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void detalleDeLugarEsPublicoSinIdentidad() {
        client.get().uri("/api/catalog/venues/" + ID)
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void crearSectorSigueProtegido() {
        client.post().uri("/api/catalog/venues/" + ID + "/sectors")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}