package ar.edu.unicen.ticketya.orders.web.dto;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID eventoId,
        String eventoNombre,
        OffsetDateTime eventoFecha,
        UUID sectorId,
        UUID asientoId,
        UUID qrCode,
        String estado,
        OffsetDateTime createdAt) {

    public static TicketResponse from(Entrada entrada) {
        return new TicketResponse(
                entrada.getId(),
                entrada.getEventoId(),
                entrada.getEventoNombre(),
                entrada.getEventoFecha(),
                entrada.getSectorId(),
                entrada.getAsientoId(),
                entrada.getQrCode(),
                entrada.getEstado().name(),
                entrada.getCreatedAt());
    }
}
