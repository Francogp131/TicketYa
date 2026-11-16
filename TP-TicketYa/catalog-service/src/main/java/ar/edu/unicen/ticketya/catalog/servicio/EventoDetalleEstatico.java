package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EventoDetalleEstatico(
        UUID id,
        String nombre,
        String descripcion,
        TipoEvento tipo,
        UUID lugarId,
        String lugarNombre,
        String ciudad,
        UUID organizadorId,
        OffsetDateTime fecha,
        EstadoEvento estado,
        Integer limitePorCliente,
        boolean altaDemanda,
        List<String> participantes,
        List<SectorEstatico> sectores) {

    public record SectorEstatico(
            UUID sectorId,
            String nombre,
            TipoSector tipo,
            BigDecimal precio,
            Integer capacidad) {
    }
}
