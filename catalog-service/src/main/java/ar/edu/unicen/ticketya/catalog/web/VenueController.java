package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.servicio.VenueService;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearLugarRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearSectorRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.LugarResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PageResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.SectorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Lugares y sectores", description = "Alta y consulta de lugares y sectores")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @Operation(summary = "Crear lugar", description = "Crea un lugar con coordenadas; solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lugar creado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN")})
    public ResponseEntity<LugarResponse> crear(
            @Valid @RequestBody CrearLugarRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity.require(userId, userRole).requireRole("ADMIN");
        LugarResponse response = venueService.crearLugar(request);
        return ResponseEntity.created(URI.create("/api/catalog/venues/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar lugares", description = "Lista lugares paginados.")
    public PageResponse<LugarResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return venueService.listarLugares(page, size);
    }

    @GetMapping("/{venueId}")
    @Operation(summary = "Detalle de lugar", description = "Devuelve un lugar por id; 404 VENUE_NOT_FOUND si no existe.")
    public LugarResponse obtener(@PathVariable UUID venueId) {
        return venueService.obtenerLugar(venueId);
    }

    @PostMapping("/{venueId}/sectors")
    @Operation(summary = "Crear sector", description = "Crea un sector numerado (genera asientos) o no numerado; solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sector creado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "404", description = "VENUE_NOT_FOUND")})
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
    @Operation(summary = "Listar sectores", description = "Lista los sectores de un lugar.")
    public List<SectorResponse> listarSectores(@PathVariable UUID venueId) {
        return venueService.listarSectores(venueId);
    }
}
