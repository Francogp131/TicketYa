package ar.edu.unicen.ticketya.recommendation.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Sobre de eventos de dominio definido en la tarea 1.5. El consumidor solo
 * procesa type=orden.pagada; el resto de los tipos se descarta o va a la DLQ.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrdenPagadaEvent(
        String eventId,
        String type,
        Instant occurredAt,
        String correlationId,
        Integer version,
        OrdenPagadaPayload payload) {

    public static final String TIPO = "orden.pagada";
}
