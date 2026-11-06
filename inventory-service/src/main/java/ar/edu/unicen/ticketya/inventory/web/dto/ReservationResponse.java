package ar.edu.unicen.ticketya.inventory.web.dto;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Reserva con el snapshot del evento y el total calculado por el servidor.")
public record ReservationResponse(
        @Schema(example = "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e") UUID id,
        @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID eventoId,
        @Schema(example = "Festival de Rock Tandil 2027") String eventoNombre,
        @Schema(example = "2027-03-14T21:00:00-03:00") OffsetDateTime eventoFecha,
        @Schema(example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e") UUID clienteId,
        @Schema(example = "VIGENTE") EstadoReserva estado,
        @Schema(example = "2026-11-05T19:10:00-03:00") OffsetDateTime expiresAt,
        List<Item> items,
        @Schema(example = "25000.00") BigDecimal total) {

    @Schema(name = "ItemReserva")
    public record Item(
            @Schema(example = "b0000000-0000-4000-8000-000000000001") UUID sectorId,
            @Schema(description = "Nulo en una zona no numerada.", example = "c0000000-0000-4000-8000-010101000000")
            UUID asientoId,
            @Schema(example = "1") int cantidad,
            @Schema(example = "25000.00") BigDecimal precioUnitario,
            @Schema(example = "25000.00") BigDecimal subtotal) {
    }
}
