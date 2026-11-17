package ar.edu.unicen.ticketya.notification.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoEvent(
        String eventId,
        String type,
        Instant occurredAt,
        String correlationId,
        Integer version,
        EventoPayload payload) {

    public static final String PUBLICADO = "evento.publicado";
    public static final String ACTUALIZADO = "evento.actualizado";
    public static final String CANCELADO = "evento.cancelado";
}
