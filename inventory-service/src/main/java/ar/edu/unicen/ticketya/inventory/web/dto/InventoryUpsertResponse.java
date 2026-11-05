package ar.edu.unicen.ticketya.inventory.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Resultado del alta. idempotente es true cuando la misma version ya estaba aplicada.")
public record InventoryUpsertResponse(
        @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID eventoId,
        @Schema(example = "2") int sectores,
        @Schema(example = "20") int asientos,
        @Schema(example = "true") boolean idempotente) {
}
