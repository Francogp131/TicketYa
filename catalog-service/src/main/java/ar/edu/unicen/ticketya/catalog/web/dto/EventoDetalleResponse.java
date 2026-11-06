package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "EventoDetalleResponse", description = "Detalle del evento con disponibilidad por sector")
public record EventoDetalleResponse(
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
        List<String> participantes,
        List<SectorDetalleResponse> sectores) {
}
