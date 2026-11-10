package ar.edu.unicen.ticketya.orders.web.dto;

import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Solicitud de compra a partir de una reserva vigente")
public record CrearOrdenRequest(
        @NotNull
        @Schema(description = "ID de la reserva a confirmar", example = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
        UUID reservaId,
        @NotNull
        @Schema(description = "Medio de pago", example = "TARJETA")
        MedioPago medioPago,
        @Schema(description = "Token mock de pasarela: APROBADO, RECHAZADO o LENTO", example = "APROBADO")
        String token) {
}
