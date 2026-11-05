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
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
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
    public EventoResponse publicar(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("ORGANIZER");
        return eventService.publicar(eventoId, identity.userId());
    }

    @PostMapping("/{eventoId}/cancel")
    public CancelacionResponse cancelar(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("ADMIN");
        return eventService.cancelar(eventoId);
    }

    @GetMapping
    public PageResponse<EventoBusquedaResponse> buscar(
            @RequestParam(required = false) TipoEvento tipo,
            @RequestParam(required = false) UUID organizadorId,
            @RequestParam(required = false) String participante,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaDesde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHasta,
            @RequestParam(defaultValue = "FECHA_ASC") String orden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return eventService.buscar(tipo, organizadorId, participante, ciudad,
                fechaDesde, fechaHasta, orden, page, size);
    }

    @GetMapping("/{eventoId}")
    public EventoDetalleResponse detalle(@PathVariable UUID eventoId) {
        return eventService.detalle(eventoId);
    }
}
