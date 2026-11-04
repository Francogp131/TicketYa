package ar.edu.unicen.ticketya.orders.servicio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta del endpoint interno de inventory. Incluye nombre y fecha del evento
 * para que orders guarde su snapshot sin consultar catalog en el camino de compra.
 */
public record ReservationDto(
        UUID id,
        UUID eventoId,
        String eventoNombre,
        OffsetDateTime eventoFecha,
        UUID clienteId,
        String estado,
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
