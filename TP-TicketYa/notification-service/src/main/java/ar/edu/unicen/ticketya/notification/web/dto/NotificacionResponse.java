package ar.edu.unicen.ticketya.notification.web.dto;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificacionResponse(
        UUID id,
        UUID eventoId,
        String tipo,
        String asunto,
        String contenido,
        EstadoNotificacion estado,
        int intentos,
        OffsetDateTime proximoReintento,
        OffsetDateTime createdAt,
        OffsetDateTime enviadaAt) {
}
