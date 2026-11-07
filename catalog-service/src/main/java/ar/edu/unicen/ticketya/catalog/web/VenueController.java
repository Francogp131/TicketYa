package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.servicio.VenueService;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearLugarRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearSectorRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.LugarResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PageResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.SectorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/venues")
@Tag(name = "Lugares", description = "Lugares, sectores y asientos")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @Operation(summary = "Crear un lugar", description = "Requiere rol ADMIN.")
    @ApiResponse(responseCode = "201", description = "Lugar creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol insuficiente (FORBIDDEN)")
    public ResponseEntity<LugarResponse> crear(
            @Valid @RequestBody CrearLugarRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity.require(userId, userRole).requireRole("ADMIN");
        LugarResponse response = venueService.crearLugar(request);
        return ResponseEntity.created(URI.create("/api/catalog/venues/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar lugares", description = "Listado paginado de lugares.")
    @ApiResponse(responseCode = "200", description = "Página de lugares")
    public PageResponse<LugarResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return venueService.listarLugares(page, size);
    }

    @GetMapping("/{venueId}")
    @Operation(summary = "Obtener un lugar")
    @ApiResponse(responseCode = "200", description = "Detalle del lugar")
    @ApiResponse(responseCode = "404", description = "El lugar no existe (VENUE_NOT_FOUND)")
    public LugarResponse obtener(@PathVariable UUID venueId) {
        return venueService.obtenerLugar(venueId);
    }

    @PostMapping("/{venueId}/sectors")
    @Operation(summary = "Crear un sector",
            description = "Un sector NUMERADO genera filas * asientosPorFila asientos. Requiere rol ADMIN.")
    @ApiResponse(responseCode = "201", description = "Sector creado y asientos generados")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol insuficiente (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "El lugar no existe (VENUE_NOT_FOUND)")
    public ResponseEntity<SectorResponse> crearSector(
            @PathVariable UUID venueId,
            @Valid @RequestBody CrearSectorRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity.require(userId, userRole).requireRole("ADMIN");
        SectorResponse response = venueService.crearSector(venueId, request);
        return ResponseEntity.created(URI.create("/api/catalog/venues/" + venueId + "/sectors/" + response.id()))
                .body(response);
    }

    @GetMapping("/{venueId}/sectors")
    @Operation(summary = "Listar sectores de un lugar")
    @ApiResponse(responseCode = "200", description = "Sectores del lugar")
    @ApiResponse(responseCode = "404", description = "El lugar no existe (VENUE_NOT_FOUND)")
    public List<SectorResponse> listarSectores(@PathVariable UUID venueId) {
        return venueService.listarSectores(venueId);
    }
}
