package ar.edu.unicen.ticketya.notification.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoPayload(
        String eventoId,
        String nombre,
        String tipo,
        List<String> artistas,
        String ciudad,
        Instant fechaEvento,
        String organizadorId,
        String estado) {
}
