package ar.edu.unicen.ticketya.orders.web.dto;

import ar.edu.unicen.ticketya.orders.dominio.Devolucion;
import ar.edu.unicen.ticketya.orders.dominio.EstadoDevolucion;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReturnResponse(
        UUID id,
        UUID entradaId,
        EstadoDevolucion estado,
        String motivo,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt) {

    public static ReturnResponse from(Devolucion devolucion) {
        return new ReturnResponse(
                devolucion.getId(),
                devolucion.getEntradaId(),
                devolucion.getEstado(),
                devolucion.getMotivo(),
                devolucion.getCreatedAt(),
                devolucion.getResolvedAt());
    }
}
