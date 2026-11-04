package ar.edu.unicen.ticketya.inventory.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InventoryUpsertRequest(
        @NotNull UUID eventoId,
        @NotBlank String nombre,
        @NotNull OffsetDateTime fecha,
        @Min(1) int limitePorCliente,
        @Min(1) long version,
        @NotEmpty List<@Valid SectorRequest> sectores) {

    public record SectorRequest(
            @NotNull UUID sectorId,
            @NotBlank String nombre,
            @NotBlank String tipo,
            @NotNull BigDecimal precio,
            @Min(1) int capacidad,
            Integer filas,
            List<@Valid AsientoRequest> asientos) {
    }

    public record AsientoRequest(
            @NotNull UUID asientoId,
            @Min(1) int fila,
            @Min(1) int numero) {
    }
}
