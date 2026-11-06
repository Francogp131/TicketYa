package ar.edu.unicen.ticketya.catalog.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "ActualizarDisponibilidadRequest",
        description = "Callback interno de inventory para sincronizar el estado del evento")
public record DisponibilidadRequest(
        @Schema(description = "true cuando no queda disponibilidad; false cuando vuelve a haber lugares",
                example = "true")
        @NotNull Boolean agotado) {
}
