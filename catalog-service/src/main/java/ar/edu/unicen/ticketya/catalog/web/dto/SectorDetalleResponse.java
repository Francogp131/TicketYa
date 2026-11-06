package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "SectorDetalleResponse", description = "Sector con precio y disponibilidad actual")
public record SectorDetalleResponse(
        UUID sectorId,
        String nombre,
        TipoSector tipo,
        BigDecimal precio,
        Integer capacidad,
        Integer disponibles) {
}
