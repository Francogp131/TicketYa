package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "SectorReporteResponse", description = "Sector con capacidad y precio vigente para reportes internos")
public record SectorReporteResponse(
        UUID sectorId,
        String nombre,
        TipoSector tipo,
        Integer capacidad,
        BigDecimal precio) {
}
