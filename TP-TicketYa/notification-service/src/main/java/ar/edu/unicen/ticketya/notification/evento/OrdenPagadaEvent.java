package ar.edu.unicen.ticketya.notification.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Sobre de eventos de dominio definido en la tarea 1.5. El consumidor solo
 * procesa type=orden.pagada; el resto se rechaza y queda en la DLQ.
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
