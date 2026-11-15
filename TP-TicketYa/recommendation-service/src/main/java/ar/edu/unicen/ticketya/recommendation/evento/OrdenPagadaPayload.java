package ar.edu.unicen.ticketya.recommendation.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * Datos mínimos de una compra pagada para construir el historial. El evento
 * es el snapshot que publica orders-service en la tarea 6.3.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrdenPagadaPayload(
        String ordenId,
        String clienteId,
        String eventoId,
        String eventoNombre,
        String eventoTipo,
        List<String> artistas,
        String ciudad,
        Instant fechaEvento,
        String estado) {

    public static final String ESTADO_PAGADA = "PAGADA";
}
