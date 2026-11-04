package ar.edu.unicen.ticketya.inventory.web.dto;

import java.util.UUID;

public record InventoryUpsertResponse(UUID eventoId, int sectores, int asientos, boolean idempotente) {
}
