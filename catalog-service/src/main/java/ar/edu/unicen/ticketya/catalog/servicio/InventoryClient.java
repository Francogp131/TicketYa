package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.Map;
import java.util.UUID;

public interface InventoryClient {

    void publicarInventario(InventorySnapshot snapshot);

    /**
     * Frena la venta en inventory cuando el evento se cancela o finaliza.
     */
    void cambiarEstado(UUID eventoId, String estado);

    /**
     * Devuelve, por sectorId, la cantidad de lugares disponibles.
     */
    Map<UUID, Integer> obtenerDisponibilidad(UUID eventoId);
}
