package ar.edu.unicen.ticketya.notification.evento;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Consulta el endpoint interno de búsqueda de preferencias por artista y
 * ciudad. Si recommendation-service no está disponible o aún no expone la
 * búsqueda, degrada a lista vacía para no bloquear la publicación del evento.
 */
@Component
public class PreferenceClient {

    private static final Logger log = LoggerFactory.getLogger(PreferenceClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public PreferenceClient(RestClient preferenceRestClient,
                            @Value("${notification.internal-token:ticketya-internal-dev}") String internalToken) {
        this.restClient = preferenceRestClient;
        this.internalToken = internalToken;
    }

    public List<PreferenciaCliente> buscarPorArtistaYCiudad(String artista, String ciudad) {
        try {
            List<PreferenciaCliente> encontrados = restClient.get()
                    .uri(uri -> uri.path("/internal/preferences")
                            .queryParam("artista", artista)
                            .queryParam("ciudad", ciudad)
                            .build())
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return encontrados == null ? List.of() : encontrados;
        } catch (RestClientException ex) {
            log.warn("Búsqueda de preferencias no disponible (artista={}, ciudad={}): {}",
                    artista, ciudad, ex.getMessage());
            return List.of();
        }
    }
}
