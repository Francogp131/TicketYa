package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryRestClient implements InventoryClient {

    private final RestClient restClient;

    public InventoryRestClient(RestClient.Builder builder,
                               @Value("${inventory.url:http://localhost:8082}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void publicarInventario(InventorySnapshot snapshot) {
        restClient.post()
                .uri("/internal/events/{eventoId}/inventory", snapshot.eventoId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(snapshot)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public Map<UUID, Integer> obtenerDisponibilidad(UUID eventoId) {
        DisponibilidadResponse response = restClient.get()
                .uri("/api/inventory/events/{eventoId}/availability", eventoId)
                .retrieve()
                .body(DisponibilidadResponse.class);
        Map<UUID, Integer> disponibilidad = new LinkedHashMap<>();
        if (response != null && response.sectores() != null) {
            response.sectores().forEach(sector -> disponibilidad.put(sector.sectorId(), sector.disponibles()));
        }
        return disponibilidad;
    }
}
