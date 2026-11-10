package ar.edu.unicen.ticketya.orders.servicio;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryRestClient implements InventoryClient {

    private final RestClient restClient;

    public InventoryRestClient(RestClient.Builder builder,
                               @Value("${inventory.url:http://localhost:8082}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ReservationDto obtenerReserva(UUID reservaId) {
        return restClient.get()
                .uri("/internal/reservations/{reservaId}", reservaId)
                .retrieve()
                .body(ReservationDto.class);
    }

    @Override
    public void confirmarReserva(UUID reservaId) {
        restClient.post()
                .uri("/internal/reservations/{reservaId}/confirm", reservaId)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void liberarReserva(UUID reservaId, String motivo) {
        restClient.post()
                .uri("/internal/reservations/{reservaId}/release", reservaId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReleaseDto(motivo))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void devolverItems(ReturnDto devolucion) {
        restClient.post()
                .uri("/internal/returns")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", devolucion.referencia().toString())
                .body(devolucion)
                .retrieve()
                .toBodilessEntity();
    }

    private record ReleaseDto(String motivo) {
    }
}
