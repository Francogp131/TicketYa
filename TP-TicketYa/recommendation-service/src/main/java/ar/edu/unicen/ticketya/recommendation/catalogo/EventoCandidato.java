package ar.edu.unicen.ticketya.recommendation.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * Candidato que devuelve el endpoint interno de catalog (tarea 3.5). El
 * contrato esperado está documentado en el README del servicio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoCandidato(
        String eventoId,
        String nombre,
        String tipo,
        List<String> artistas,
        String ciudad,
        Instant fechaEvento,
        String estado,
        boolean altaDemanda) {
}
