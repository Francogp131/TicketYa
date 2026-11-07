package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@Tag(name = "Eventos", description = "Eventos, precios, publicación, estados y búsqueda")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @Operation(summary = "Crear un evento", description = "El evento nace en BORRADOR. Requiere rol ORGANIZER.")
    @ApiResponse(responseCode = "201", description = "Evento creado")
    @ApiResponse(responseCode = "400", description = "Fecha pasada o datos inválidos (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol insuficiente (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "El lugar no existe (VENUE_NOT_FOUND)")
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
    @Operation(summary = "Asignar el precio de un sector",
            description = "Solo sobre eventos en BORRADOR y del organizador autenticado.")
    @ApiResponse(responseCode = "200", description = "Precio asignado")
    @ApiResponse(responseCode = "400", description = "Precio negativo (INVALID_PRICE) o sector ajeno (SECTOR_NOT_FROM_VENUE)")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol insuficiente o evento de otro organizador (FORBIDDEN, NOT_EVENT_OWNER)")
    @ApiResponse(responseCode = "404", description = "Evento o sector inexistente")
    @ApiResponse(responseCode = "409", description = "El evento ya no es editable (EVENT_NOT_EDITABLE)")
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
    @Operation(summary = "Publicar un evento",
            description = "Envía el snapshot a inventory de forma idempotente. Si inventory falla, el evento sigue en BORRADOR.")
    @ApiResponse(responseCode = "200", description = "Evento publicado")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol insuficiente o evento de otro organizador (FORBIDDEN, NOT_EVENT_OWNER)")
    @ApiResponse(responseCode = "404", description = "El evento no existe (EVENT_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Evento incompleto (EVENT_INCOMPLETE) o no editable (EVENT_NOT_EDITABLE)")
    @ApiResponse(responseCode = "502", description = "Inventory no confirmó el snapshot (INVENTORY_UNAVAILABLE)")
    public EventoResponse publicar(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("ORGANIZER");
        return eventService.publicar(eventoId, identity.userId());
    }

    @PostMapping("/{eventoId}/cancel")
    @Operation(summary = "Cancelar un evento",
            description = "Requiere ADMIN. Si orders no responde, responde 200 con cancelacionPendiente=true.")
    @ApiResponse(responseCode = "200", description = "Evento cancelado")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol insuficiente (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "El evento no existe (EVENT_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "El evento ya está cancelado o finalizado (EVENT_NOT_CANCELLABLE)")
    public CancelacionResponse cancelar(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("ADMIN");
        return eventService.cancelar(eventoId);
    }

    @GetMapping
    @Operation(summary = "Buscar eventos",
            description = "Solo devuelve eventos PUBLICADO o AGOTADO. Los filtros se combinan entre sí.")
    @ApiResponse(responseCode = "200", description = "Página de eventos")
    @ApiResponse(responseCode = "400", description = "Rango de fechas inválido (INVALID_DATE_RANGE) u orden inválido (INVALID_SORT)")
    public PageResponse<EventoBusquedaResponse> buscar(
            @Parameter(description = "Tipo de evento") @RequestParam(required = false) TipoEvento tipo,
            @Parameter(description = "Organizador dueño del evento") @RequestParam(required = false) UUID organizadorId,
            @Parameter(description = "Nombre de un participante") @RequestParam(required = false) String participante,
            @Parameter(description = "Ciudad del lugar") @RequestParam(required = false) String ciudad,
            @Parameter(description = "Desde (ISO-8601 con offset)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaDesde,
            @Parameter(description = "Hasta (ISO-8601 con offset)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHasta,
            @Parameter(description = "FECHA_ASC, FECHA_DESC, PRECIO_ASC, PRECIO_DESC o RELEVANCIA")
            @RequestParam(defaultValue = "FECHA_ASC") String orden,
            @Parameter(description = "Número de página, desde 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página, entre 1 y 100") @RequestParam(defaultValue = "20") int size) {
        return eventService.buscar(tipo, organizadorId, participante, ciudad,
                fechaDesde, fechaHasta, orden, page, size);
    }

    @GetMapping("/{eventoId}")
    @Operation(summary = "Detalle de un evento",
            description = "Incluye sectores, precios y disponibilidad consultada a inventory. Un evento BORRADOR responde 404.")
    @ApiResponse(responseCode = "200", description = "Detalle del evento")
    @ApiResponse(responseCode = "404", description = "El evento no existe o no es visible (EVENT_NOT_FOUND)")
    @ApiResponse(responseCode = "503", description = "No se pudo consultar inventory (INVENTORY_UNAVAILABLE)")
    public EventoDetalleResponse detalle(@PathVariable UUID eventoId) {
        return eventService.detalle(eventoId);
    }
}
