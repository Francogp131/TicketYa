package ar.edu.unicen.ticketya.notification.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class PreferenceClientTest {

    @Test
    void buscaClientesPorArtistaYCiudadConElTokenInterno() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PreferenceClient client = new PreferenceClient(
                builder.baseUrl("http://recommendation:8084").build(), "secreto");
        server.expect(requestTo(uri("Los Piojos", "Tandil")))
                .andExpect(header("X-Internal-Token", "secreto"))
                .andRespond(withSuccess("""
                        [{"clienteId":"33333333-3333-4333-8333-333333333333","clienteEmail":"c@example.com"}]
                        """, MediaType.APPLICATION_JSON));

        List<PreferenciaCliente> encontrados = client.buscarPorArtistaYCiudad("Los Piojos", "Tandil");

        assertThat(encontrados).containsExactly(
                new PreferenciaCliente("33333333-3333-4333-8333-333333333333", "c@example.com"));
        server.verify();
    }

    @Test
    void siPreferenciasNoRespondeDegradaAListaVacia() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PreferenceClient client = new PreferenceClient(
                builder.baseUrl("http://recommendation:8084").build(), "secreto");
        server.expect(requestTo(uri("Los Piojos", "Tandil"))).andRespond(withServerError());

        assertThat(client.buscarPorArtistaYCiudad("Los Piojos", "Tandil")).isEmpty();
        server.verify();
    }

    private java.net.URI uri(String artista, String ciudad) {
        return UriComponentsBuilder.fromHttpUrl("http://recommendation:8084/internal/preferences")
                .queryParam("artista", artista)
                .queryParam("ciudad", ciudad)
                .build()
                .encode()
                .toUri();
    }
}
