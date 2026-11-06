package ar.edu.unicen.ticketya.inventory.web.openapi;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Forma RFC 7807 que documenta los errores. La respuesta real la arma {@code GlobalExceptionHandler}.
 */
@Schema(name = "Problema", description = "Error application/problem+json con el código estable de TicketYa.")
public record Problema(
        @Schema(example = "https://ticketya.local/errors/seat-not-available") String type,
        @Schema(example = "Conflict") String title,
        @Schema(example = "409") int status,
        @Schema(example = "El asiento ya no está disponible") String detail,
        @Schema(example = "/api/inventory/reservations") String instance,
        @Schema(example = "SEAT_NOT_AVAILABLE") String code,
        List<Campo> errors) {

    @Schema(name = "CampoInvalido")
    public record Campo(
            @Schema(example = "items") String field,
            @Schema(example = "un ítem numerado requiere asientoIds y no admite cantidad") String message) {
    }
}
