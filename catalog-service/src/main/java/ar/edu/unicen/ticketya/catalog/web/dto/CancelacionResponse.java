package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import java.util.UUID;

public record CancelacionResponse(
        UUID eventoId,
        EstadoEvento estado,
        boolean cancelacionPendiente) {
}
