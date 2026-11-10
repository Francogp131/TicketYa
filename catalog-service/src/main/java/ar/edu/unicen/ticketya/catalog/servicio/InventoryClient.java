package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.Map;
import java.util.UUID;

public interface InventoryClient {

    void publicarInventario(InventorySnapshot snapshot);

    /**
     * Avisa a inventory que el evento llegó a un estado terminal (CANCELADO o FINALIZADO).
     * Es idempotente: repetir el mismo estado responde 204 sin escribir.
     */
    void actualizarEstado(UUID eventoId, String estado);

    /**
     * Devuelve, por sectorId, la cantidad de lugares disponibles.
     */
    Map<UUID, Integer> obtenerDisponibilidad(UUID eventoId);
}
