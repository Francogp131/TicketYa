package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.web.dto.DisponibilidadRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
@Tag(name = "Interno", description = "Endpoints entre servicios; no se exponen por el gateway")
public class InternalEventController {

    private final EventService eventService;
    private final InternalToken internalToken;

    public InternalEventController(EventService eventService, InternalToken internalToken) {
        this.eventService = eventService;
        this.internalToken = internalToken;
    }

    @PatchMapping("/{eventoId}/availability")
    @Operation(summary = "Sincronizar disponibilidad de un evento",
            description = "Inventory lo llama cuando la disponibilidad llega a 0 (AGOTADO) o vuelve a ser positiva (PUBLICADO).")
    @ApiResponse(responseCode = "204", description = "Estado sincronizado; la operación es idempotente")
    @ApiResponse(responseCode = "400", description = "Cuerpo inválido (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Token interno ausente o inválido (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "El evento no existe (EVENT_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "La transición de estado no es válida (EVENT_NOT_EDITABLE)")
    public ResponseEntity<Void> actualizarDisponibilidad(
            @PathVariable UUID eventoId,
            @Valid @RequestBody DisponibilidadRequest request,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalToken.require(token);
        eventService.actualizarDisponibilidad(eventoId, request.agotado());
        return ResponseEntity.noContent().build();
    }
}