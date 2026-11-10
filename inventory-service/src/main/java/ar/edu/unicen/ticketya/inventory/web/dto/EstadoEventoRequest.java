package ar.edu.unicen.ticketya.inventory.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Estado terminal que catalog avisa a inventory.")
public record EstadoEventoRequest(
        @NotBlank @Schema(example = "CANCELADO", allowableValues = {"CANCELADO", "FINALIZADO"})
        String estado) {
}
