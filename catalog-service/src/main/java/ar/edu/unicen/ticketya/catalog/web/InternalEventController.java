package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.web.dto.ActualizarDisponibilidadRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
@Tag(name = "Interno", description = "Endpoints internos, no expuestos por el gateway")
public class InternalEventController {

    private final EventService eventService;

    public InternalEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PatchMapping("/{eventoId}/availability")
    @Operation(summary = "Callback de disponibilidad", description = "inventory avisa si el evento quedó AGOTADO o volvió a PUBLICADO. Idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Estado actualizado o sin cambios"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR"),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND")})
    public ResponseEntity<Void> disponibilidad(@PathVariable UUID eventoId,
                                               @Valid @RequestBody ActualizarDisponibilidadRequest request) {
        eventService.actualizarDisponibilidad(eventoId, request.agotado());
        return ResponseEntity.noContent().build();
    }
}
