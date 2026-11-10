package ar.edu.unicen.ticketya.inventory.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReturnRequest(
        @NotNull UUID eventoId,
        @NotEmpty List<@Valid Item> items,
        @Schema(description = "Dueño de las entradas. Obligatorio para reponer el cupo de una zona. Si falta, la zona vuelve a estar disponible pero el límite del cliente no se libera.",
                example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID clienteId) {

    public record Item(
            @NotNull UUID sectorId,
            UUID asientoId,
            @NotNull @Min(1) Integer cantidad) {
    }
}
