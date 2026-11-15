package ar.edu.unicen.ticketya.recommendation.catalogo;

import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente del endpoint interno de candidatos de catalog (tarea 3.5).
 * Timeout corto y dos intentos para errores transitorios; ante cualquier fallo
 * el servicio cae al snapshot local de eventos_vistos.
 */
@Component
public class CatalogoRestClient implements CatalogoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoRestClient.class);
    static final int MAX_INTENTOS = 2;
    static final int TAMANIO_CANDIDATOS = 20;

    private final RestClient restClient;

    public CatalogoRestClient(RestClient.Builder builder,
                              @Value("${recommendation.catalog.url:http://localhost:8081}") String baseUrl,
                              @Value("${recommendation.catalog.timeout.connect-ms:2000}") int connectTimeoutMs,
                              @Value("${recommendation.catalog.timeout.read-ms:3000}") int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public List<EventoCandidato> buscarCandidatos(Preferencia preferencia, Instant fechaDesde) {
        try {
            List<EventoCandidato> candidatos = conReintentos(() -> restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/internal/recommendations/candidates")
                                .queryParam("fechaDesde", fechaDesde.toString())
                                .queryParam("size", TAMANIO_CANDIDATOS);
                        if (preferencia != null && preferencia.getCiudad() != null
                                && !preferencia.getCiudad().isBlank()) {
                            // El ranking por tipo/artista se hace localmente para no
                            // perder candidatos de co-compra que no coinciden con el gusto.
                            uriBuilder.queryParam("ciudad", preferencia.getCiudad());
                        }
                        return uriBuilder.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EventoCandidato>>() {
                    }));
            return candidatos == null ? List.of() : candidatos;
        } catch (RuntimeException ex) {
            log.warn("catalog no respondió candidatos; se usa el snapshot local: {}", ex.getMessage());
            throw new CatalogoUnavailableException("No se pudieron obtener candidatos de catalog", ex);
        }
    }

    private <T> T conReintentos(Supplier<T> operacion) {
        RuntimeException ultimo = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return operacion.get();
            } catch (HttpClientErrorException ex) {
                // 4xx es definitivo: reintentar no cambia el resultado.
                throw ex;
            } catch (RestClientException | IllegalStateException ex) {
                ultimo = ex;
                if (intento < MAX_INTENTOS) {
                    esperar();
                }
            }
        }
        throw ultimo == null ? new IllegalStateException("catalog no respondió") : ultimo;
    }

    private void esperar() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió el reintento contra catalog", ex);
        }
    }
}
