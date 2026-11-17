package ar.edu.unicen.ticketya.catalog.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "EventoCandidatoResponse",
        description = "Evento publicado o agotado con los datos mínimos para rankear recomendaciones")
public record EventoCandidatoResponse(
        String eventoId,
        String nombre,
        String tipo,
        List<String> artistas,
        String ciudad,
        Instant fechaEvento,
        String estado,
        boolean altaDemanda) {
}
