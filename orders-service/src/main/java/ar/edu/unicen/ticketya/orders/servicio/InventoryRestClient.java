package ar.edu.unicen.ticketya.orders.servicio;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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
    public ReservationDto obtenerReserva(UUID reservaId) {
        return restClient.get()
                .uri("/internal/reservations/{reservaId}", reservaId)
                .retrieve()
                .body(ReservationDto.class);
    }
}
