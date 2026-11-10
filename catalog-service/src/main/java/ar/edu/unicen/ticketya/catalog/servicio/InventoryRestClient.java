package ar.edu.unicen.ticketya.catalog.servicio;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class InventoryRestClient implements InventoryClient {

    static final int MAX_INTENTOS = 3;
    static final Duration ESPERA_BASE = Duration.ofMillis(100);

    private final RestClient restClient;

    public InventoryRestClient(RestClient.Builder builder,
                               @Value("${inventory.url:http://localhost:8082}") String baseUrl,
                               @Value("${inventory.timeout.connect-ms:2000}") int connectTimeoutMs,
                               @Value("${inventory.timeout.read-ms:3000}") int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public void publicarInventario(InventorySnapshot snapshot) {
        conReintentos(() -> {
            restClient.post()
                    .uri("/internal/events/{eventoId}/inventory", snapshot.eventoId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(snapshot)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public void actualizarEstado(UUID eventoId, String estado) {
        conReintentos(() -> {
            restClient.patch()
                    .uri("/internal/events/{eventoId}/state", eventoId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("estado", estado))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public Map<UUID, Integer> obtenerDisponibilidad(UUID eventoId) {
        DisponibilidadResponse response = conReintentos(() -> restClient.get()
                .uri("/api/inventory/events/{eventoId}/availability", eventoId)
                .retrieve()
                .body(DisponibilidadResponse.class));
        Map<UUID, Integer> disponibilidad = new LinkedHashMap<>();
        if (response != null && response.sectores() != null) {
            response.sectores().forEach(sector -> disponibilidad.put(sector.sectorId(), sector.disponibles()));
        }
        return disponibilidad;
    }

    /**
     * Reintenta solo errores transitorios (timeout, conexión y 5xx). Los 4xx son
     * definitivos: reintentarlos no cambia el resultado y oculta el error real.
     */
    private <T> T conReintentos(Supplier<T> operacion) {
        RuntimeException ultimo = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return operacion.get();
            } catch (HttpClientErrorException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                ultimo = ex;
                if (intento < MAX_INTENTOS) {
                    esperar(intento);
                }
            }
        }
        throw ultimo;
    }

    private void esperar(int intento) {
        try {
            Thread.sleep(ESPERA_BASE.toMillis() * intento);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió el reintento contra inventory", ex);
        }
    }
}
