package ar.edu.unicen.ticketya.orders.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Solicitud de devolución de una entrada")
public record ReturnRequest(
        @Size(max = 500, message = "no puede superar los 500 caracteres")
        @Schema(description = "Motivo de la devolución", example = "No puedo asistir al evento")
        String motivo) {
}
