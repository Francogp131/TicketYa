package ar.edu.unicen.ticketya.inventory.servicio;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder,
                         @Value("${catalog.url:http://localhost:8081}") String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(800));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public void notificarDisponibilidad(UUID eventoId, boolean agotado) {
        for (int intento = 1; intento <= 2; intento++) {
            try {
                restClient.patch()
                        .uri("/internal/events/{eventoId}/availability", eventoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("agotado", agotado))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RuntimeException ex) {
                log.warn("No se pudo avisar la disponibilidad a catalog (intento {}): evento={} agotado={}",
                        intento, eventoId, agotado, ex);
            }
        }
    }
}
