package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

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
