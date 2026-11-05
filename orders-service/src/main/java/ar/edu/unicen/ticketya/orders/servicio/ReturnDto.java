package ar.edu.unicen.ticketya.orders.servicio;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de liberación por devolución al inventory-service (contrato interno 4.10).
 * `referencia` permite que el endpoint sea idempotente.
 */
public record ReturnDto(
        UUID eventoId,
        UUID referencia,
        List<Item> items) {

    public record Item(UUID sectorId, UUID asientoId, Integer cantidad) {
    }
}
