package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import java.math.BigDecimal;
import java.util.UUID;

public record SectorDetalleResponse(
        UUID sectorId,
        String nombre,
        TipoSector tipo,
        BigDecimal precio,
        Integer capacidad,
        Integer disponibles) {
}
