package ar.edu.unicen.ticketya.orders.web.dto;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    public static OrderResponse from(Orden orden, List<OrdenItem> items,
                                     List<ar.edu.unicen.ticketya.orders.dominio.Entrada> entradas) {
        List<Item> itemsResponse = items.stream()
                .map(item -> new Item(item.getSectorId(), item.getAsientoId(),
                        item.getCantidad(), item.getPrecioUnitario(), item.getSubtotal()))
                .toList();
        List<Entrada> entradasResponse = entradas.stream()
                .map(entrada -> new Entrada(entrada.getId(), entrada.getQrCode(),
                        entrada.getEstado().name(), entrada.getSectorId(), entrada.getAsientoId()))
                .toList();
        return new OrderResponse(
                orden.getId(),
                orden.getClienteId(),
                orden.getReservaId(),
                orden.getEventoId(),
                orden.getEventoNombre(),
                aArgentina(orden.getEventoFecha()),
                orden.getEstado(),
                orden.getMedioPago(),
                orden.getTotal(),
                itemsResponse,
                entradasResponse,
                aArgentina(orden.getCreatedAt()));
    }

    private static OffsetDateTime aArgentina(OffsetDateTime fecha) {
        return fecha == null ? null
                : fecha.atZoneSameInstant(ZoneId.of("America/Argentina/Buenos_Aires")).toOffsetDateTime();
    }

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
