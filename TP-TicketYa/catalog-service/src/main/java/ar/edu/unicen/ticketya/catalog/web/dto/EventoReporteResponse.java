package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "EventoReporteResponse",
        description = "Datos internos del evento, organizador, participantes, sectores, capacidad y precios")
public record EventoReporteResponse(
        UUID eventoId,
        String nombre,
        UUID organizadorId,
        UUID lugarId,
        String lugarNombre,
        String ciudad,
        OffsetDateTime fecha,
        EstadoEvento estado,
        List<String> participantes,
        List<SectorReporteResponse> sectores) {
}
