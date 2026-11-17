package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoCandidatoResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoReporteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@Tag(name = "Interno", description = "Endpoints entre servicios; no se exponen por el gateway")
public class InternalCatalogController {

    private final EventService eventService;
    private final InternalToken internalToken;

    public InternalCatalogController(EventService eventService, InternalToken internalToken) {
        this.eventService = eventService;
        this.internalToken = internalToken;
    }

    @GetMapping("/events/{eventoId}/report")
    @Operation(summary = "Datos de reporte de un evento",
            description = "Expone organizador, participantes, sectores, capacidad y precios vigentes para reportes.")
    @ApiResponse(responseCode = "200", description = "Datos internos del evento")
    @ApiResponse(responseCode = "401", description = "Token interno ausente o inválido (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "El evento no existe (EVENT_NOT_FOUND)")
    public EventoReporteResponse reporte(
            @PathVariable UUID eventoId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalToken.require(token);
        return eventService.reporteEvento(eventoId);
    }

    @GetMapping("/recommendations/candidates")
    @Operation(summary = "Candidatos para recomendaciones",
            description = "Devuelve solo eventos publicados o agotados con los datos mínimos para rankear.")
    @ApiResponse(responseCode = "200", description = "Candidatos que cumplen los filtros")
    @ApiResponse(responseCode = "400", description = "Filtros inválidos (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Token interno ausente o inválido (UNAUTHORIZED)")
    public List<EventoCandidatoResponse> candidatos(
            @RequestParam(required = false) TipoEvento tipo,
            @RequestParam(required = false) String artista,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaDesde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHasta,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalToken.require(token);
        return eventService.candidatos(tipo, artista, ciudad, fechaDesde, fechaHasta, size);
    }
}
