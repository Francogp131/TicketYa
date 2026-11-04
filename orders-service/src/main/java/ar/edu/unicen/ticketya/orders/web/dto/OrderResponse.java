package ar.edu.unicen.ticketya.orders.web.dto;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID clienteId,
        UUID reservaId,
        UUID eventoId,
        String eventoNombre,
        OffsetDateTime eventoFecha,
        EstadoOrden estado,
        MedioPago medioPago,
        BigDecimal total,
        List<Item> items,
        List<Entrada> entradas,
        OffsetDateTime createdAt) {

    public record Item(
            UUID sectorId,
            UUID asientoId,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }

    public record Entrada(
            UUID id,
            UUID qrCode,
            String estado,
            UUID sectorId,
            UUID asientoId) {
    }
}
