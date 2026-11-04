package ar.edu.unicen.ticketya.inventory.web.dto;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID eventoId,
        String eventoNombre,
        OffsetDateTime eventoFecha,
        UUID clienteId,
        EstadoReserva estado,
        OffsetDateTime expiresAt,
        List<Item> items,
        BigDecimal total) {

    public record Item(
            UUID sectorId,
            UUID asientoId,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }
}
