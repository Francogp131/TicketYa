package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.Sector;
import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import java.util.UUID;

public record SectorResponse(
        UUID id,
        UUID lugarId,
        String nombre,
        TipoSector tipo,
        Integer capacidad,
        Integer filas,
        Integer asientosPorFila,
        long asientosGenerados) {

    public static SectorResponse from(Sector sector, long asientosGenerados) {
        return new SectorResponse(
                sector.getId(),
                sector.getLugarId(),
                sector.getNombre(),
                sector.getTipo(),
                sector.getCapacidad(),
                sector.getFilas(),
                sector.getAsientosPorFila(),
                asientosGenerados);
    }
}
