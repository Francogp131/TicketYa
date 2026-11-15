package ar.edu.unicen.ticketya.recommendation.web.dto;

import java.time.Instant;
import java.util.List;

public record RecomendacionesResponse(
        String clienteId,
        Instant generadoEn,
        List<RecomendacionItem> recomendaciones) {
}
