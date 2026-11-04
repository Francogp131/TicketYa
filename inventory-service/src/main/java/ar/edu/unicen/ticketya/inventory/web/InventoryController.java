package ar.edu.unicen.ticketya.inventory.web;

import ar.edu.unicen.ticketya.inventory.servicio.DisponibilidadService;
import ar.edu.unicen.ticketya.inventory.servicio.ReservaService;
import ar.edu.unicen.ticketya.inventory.web.dto.AsientoMapaResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.DisponibilidadResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
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
@RequestMapping("/api/inventory")
public class InventoryController {

    private final DisponibilidadService disponibilidadService;
    private final ReservaService reservaService;

    public InventoryController(DisponibilidadService disponibilidadService,
                               ReservaService reservaService) {
        this.disponibilidadService = disponibilidadService;
        this.reservaService = reservaService;
    }

    @GetMapping("/events/{eventoId}/availability")
    public DisponibilidadResponse disponibilidad(@PathVariable UUID eventoId) {
        return disponibilidadService.obtener(eventoId);
    }

    @GetMapping("/events/{eventoId}/seats")
    public List<AsientoMapaResponse> asientos(@PathVariable UUID eventoId,
                                              @RequestParam UUID sectorId) {
        return disponibilidadService.asientos(eventoId, sectorId).stream()
                .map(asiento -> new AsientoMapaResponse(
                        asiento.getAsientoId(), asiento.getFila(), asiento.getNumero(),
                        asiento.getEstado().name()))
                .toList();
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reservar(
            @Valid @RequestBody ReservaRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        ReservationResponse response = reservaService.reservar(request, identity.userId());
        return ResponseEntity.created(URI.create("/api/inventory/reservations/" + response.id())).body(response);
    }

    @GetMapping("/reservations/{reservaId}")
    public ReservationResponse obtener(
            @PathVariable UUID reservaId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        return reservaService.obtener(reservaId, identity.userId());
    }
}
