package ar.edu.unicen.ticketya.inventory.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Liberación por devolución (tarea 3.6). `referencia` es opcional y permite
 * deduplicar reintentos de orders; los asientos numerados ya son idempotentes
 * por estado, pero las zonas necesitan la referencia para no reponer de más.
 */
public record ReturnRequest(
        @NotNull UUID eventoId,
        UUID referencia,
        @NotEmpty List<@Valid Item> items) {

    public record Item(
            @NotNull UUID sectorId,
            UUID asientoId,
            @Min(1) Integer cantidad) {
    }
}
