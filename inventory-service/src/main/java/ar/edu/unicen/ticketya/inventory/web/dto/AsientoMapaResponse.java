package ar.edu.unicen.ticketya.inventory.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Asiento del mapa público.")
public record AsientoMapaResponse(
        @Schema(example = "c0000000-0000-4000-8000-010101000000") UUID asientoId,
        @Schema(example = "1") int fila,
        @Schema(example = "1") int numero,
        @Schema(example = "LIBRE", description = "LIBRE, RESERVADO o VENDIDO") String estado) {
}
