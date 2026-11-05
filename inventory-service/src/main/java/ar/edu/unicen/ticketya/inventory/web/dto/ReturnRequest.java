package ar.edu.unicen.ticketya.inventory.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReturnRequest(
        @NotNull UUID eventoId,
        @NotEmpty List<@Valid Item> items) {

    public record Item(
            @NotNull UUID sectorId,
            UUID asientoId,
            @NotNull @Min(1) Integer cantidad) {
    }
}
