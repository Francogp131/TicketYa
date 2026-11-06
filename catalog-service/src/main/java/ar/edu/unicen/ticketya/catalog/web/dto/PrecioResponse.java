package ar.edu.unicen.ticketya.catalog.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "PrecioResponse", description = "Precio asignado a un sector")
public record PrecioResponse(
        @Schema(description = "Evento", example = "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b") UUID eventoId,
        @Schema(description = "Sector", example = "9a1b2c3d-4e5f-4a6b-8c7d-1e2f3a4b5c6d") UUID sectorId,
        @Schema(description = "Precio", example = "15000.00") BigDecimal precio) {
}
