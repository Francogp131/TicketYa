package ar.edu.unicen.ticketya.catalog.servicio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DisponibilidadResponse(
        UUID eventoId,
        String estado,
        List<SectorDisponibilidad> sectores) {

    public record SectorDisponibilidad(
            UUID sectorId,
            String nombre,
            String tipo,
            BigDecimal precio,
            int capacidad,
            int disponibles) {
    }
}
