package ar.edu.unicen.ticketya.catalog.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(name = "PrecioRequest", description = "Precio de un sector para un evento")
public record PrecioRequest(
        @Schema(description = "Precio no negativo", example = "15000.00")
        @NotNull BigDecimal precio) {
}
