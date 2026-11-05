package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrdersRestClient implements OrdersClient {

    private final RestClient restClient;

    public OrdersRestClient(RestClient.Builder builder,
                            @Value("${orders.url:http://localhost:8083}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void cancelarEvento(UUID eventoId) {
        restClient.post()
                .uri("/internal/events/{eventoId}/cancel", eventoId)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
    }
}
