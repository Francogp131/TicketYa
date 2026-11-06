package ar.edu.unicen.ticketya.inventory.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(description = "Un ítem numerado lleva asientoIds. Un ítem de zona lleva cantidad. Nunca los dos.")
public record ReservaRequest(
        @NotNull @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID eventoId,
        @NotEmpty List<@Valid Item> items) {

    @Schema(name = "ItemPedido")
    public record Item(
            @NotNull @Schema(example = "b0000000-0000-4000-8000-000000000001") UUID sectorId,
            @Schema(description = "Asientos de un sector numerado. Se omite en una zona.")
            List<UUID> asientoIds,
            @Min(1) @Schema(description = "Lugares de una zona. Se omite si hay asientoIds.", example = "2")
            Integer cantidad) {
    }
}
