package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "EventoBusquedaResponse", description = "Resultado de búsqueda de eventos")
public record EventoBusquedaResponse(
        UUID id,
        String nombre,
        TipoEvento tipo,
        String ciudad,
        String lugarNombre,
        OffsetDateTime fecha,
        EstadoEvento estado,
        BigDecimal precioDesde) {
}
