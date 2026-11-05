package ar.edu.unicen.ticketya.orders.web.dto;

import java.util.UUID;

public record CancelacionEventoResponse(
        UUID eventoId,
        int entradasAfectadas,
        int reintegrosSolicitados) {
}
