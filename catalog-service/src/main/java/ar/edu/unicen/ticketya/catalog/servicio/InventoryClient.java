package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.Map;
import java.util.UUID;

public interface InventoryClient {

    void publicarInventario(InventorySnapshot snapshot);

    /**
     * Devuelve, por sectorId, la cantidad de lugares disponibles.
     */
    Map<UUID, Integer> obtenerDisponibilidad(UUID eventoId);
}
