package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.web.dto.CancelacionResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearEventoRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoBusquedaResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoDetalleResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PageResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PrecioRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.PrecioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/events")
@Tag(name = "Eventos", description = "Alta, precios, publicación, cancelación, búsqueda y detalle de eventos")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @Operation(summary = "Crear evento", description = "Crea un evento en estado BORRADOR para el organizador autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evento creado en BORRADOR"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN")})
    public ResponseEntity<EventoResponse> crear(
            @Valid @RequestBody CrearEventoRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("ORGANIZER");
        EventoResponse response = eventService.crearEvento(request, identity.userId());
        return ResponseEntity.created(URI.create("/api/catalog/events/" + response.id())).body(response);
    }

    @PutMapping("/{eventoId}/prices/{sectorId}")
    @Operation(summary = "Asignar precio", description = "Asigna o actualiza el precio de un sector mientras el evento está en BORRADOR.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Precio asignado"),
            @ApiResponse(responseCode = "400", description = "INVALID_PRICE o SECTOR_NOT_FROM_VENUE"),
            @ApiResponse(responseCode = "403", description = "NOT_EVENT_OWNER"),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND o SECTOR_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "EVENT_NOT_EDITABLE")})
    public PrecioResponse asignarPrecio(
            @PathVariable UUID eventoId,
            @PathVariable UUID sectorId,
            @Valid @RequestBody PrecioRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("ORGANIZER");
        return eventService.asignarPrecio(eventoId, sectorId, request, identity.userId());
    }

    @PostMapping("/{eventoId}/publish")
    @Operation(summary = "Publicar evento", description = "Valida precios, envía el snapshot a inventory y deja el evento PUBLICADO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento publicado"),
            @ApiResponse(responseCode = "403", description = "NOT_EVENT_OWNER"),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "EVENT_NOT_EDITABLE o EVENT_INCOMPLETE"),
            @ApiResponse(responseCode = "502", description = "INVENTORY_UNAVAILABLE")})
    public EventoResponse publicar(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("ORGANIZER");
        return eventService.publicar(eventoId, identity.userId());
    }

    @PostMapping("/{eventoId}/cancel")
    @Operation(summary = "Cancelar evento", description = "Cancela un evento publicado o agotado y avisa a orders; si orders no responde queda cancelacionPendiente=true.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento cancelado"),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "EVENT_NOT_CANCELLABLE")})
    public CancelacionResponse cancelar(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity.require(userId, userRole).requireRole("ADMIN");
        return eventService.cancelar(eventoId);
    }

    @GetMapping
    @Operation(summary = "Buscar eventos", description = "Búsqueda pública por tipo, organizador, participante, ciudad y rango de fechas, con orden y paginación.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de eventos PUBLICADO o AGOTADO"),
            @ApiResponse(responseCode = "400", description = "INVALID_SORT o INVALID_DATE_RANGE")})
    public PageResponse<EventoBusquedaResponse> buscar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String organizadorId,
            @RequestParam(required = false) String participante,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            @RequestParam(defaultValue = "FECHA_ASC") String orden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return eventService.buscar(tipo, organizadorId, participante, ciudad,
                fechaDesde, fechaHasta, orden, page, size);
    }

    @GetMapping("/{eventoId}")
    @Operation(summary = "Detalle de evento", description = "Devuelve el evento publicado con precios y disponibilidad actualizada desde inventory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento visible"),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND"),
            @ApiResponse(responseCode = "503", description = "INVENTORY_UNAVAILABLE")})
    public EventoDetalleResponse detalle(@PathVariable UUID eventoId) {
        return eventService.detalle(eventoId);
    }
}
