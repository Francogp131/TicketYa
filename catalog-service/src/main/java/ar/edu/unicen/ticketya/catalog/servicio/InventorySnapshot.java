package ar.edu.unicen.ticketya.catalog.servicio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot que catalog envía a inventory al publicar un evento.
 * Los nombres de los campos coinciden con el contrato interno (4.6).
 */
public record InventorySnapshot(
        UUID eventoId,
        String nombre,
        OffsetDateTime fecha,
        int limitePorCliente,
        long version,
        List<SectorSnapshot> sectores) {

    public record SectorSnapshot(
            UUID sectorId,
            String nombre,
            String tipo,
            BigDecimal precio,
            int capacidad,
            Integer filas,
            List<AsientoSnapshot> asientos) {
    }

    public record AsientoSnapshot(UUID asientoId, int fila, int numero) {
    }
}
