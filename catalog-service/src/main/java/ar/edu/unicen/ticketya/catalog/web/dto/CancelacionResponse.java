package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "CancelacionResponse", description = "Resultado de la cancelación de un evento")
public record CancelacionResponse(
        @Schema(description = "Evento cancelado", example = "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b") UUID eventoId,
        @Schema(description = "Estado resultante", example = "CANCELADO") EstadoEvento estado,
        @Schema(description = "true si orders no respondió y el scheduler reintentará", example = "false")
        boolean cancelacionPendiente) {
}
