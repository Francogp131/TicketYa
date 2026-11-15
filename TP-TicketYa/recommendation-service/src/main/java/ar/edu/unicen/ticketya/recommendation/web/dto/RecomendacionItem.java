package ar.edu.unicen.ticketya.recommendation.web.dto;

import java.time.Instant;
import java.util.List;

public record RecomendacionItem(
        String eventoId,
        String nombre,
        String tipo,
        List<String> artistas,
        String ciudad,
        Instant fechaEvento,
        boolean altaDemanda,
        int puntaje,
        List<String> motivos) {
}
