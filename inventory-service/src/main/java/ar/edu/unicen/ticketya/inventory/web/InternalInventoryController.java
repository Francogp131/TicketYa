package ar.edu.unicen.ticketya.inventory.web;

import ar.edu.unicen.ticketya.inventory.servicio.ConfirmacionService;
import ar.edu.unicen.ticketya.inventory.servicio.InventarioService;
import ar.edu.unicen.ticketya.inventory.servicio.ReservaService;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReleaseRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReturnRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalInventoryController {

    private final InventarioService inventarioService;
    private final ReservaService reservaService;
    private final ConfirmacionService confirmacionService;

    public InternalInventoryController(InventarioService inventarioService,
                                       ReservaService reservaService,
                                       ConfirmacionService confirmacionService) {
        this.inventarioService = inventarioService;
        this.reservaService = reservaService;
        this.confirmacionService = confirmacionService;
    }

    @PostMapping("/events/{eventoId}/inventory")
    public InventoryUpsertResponse upsert(@PathVariable UUID eventoId,
                                          @Valid @RequestBody InventoryUpsertRequest request) {
        return inventarioService.upsert(eventoId, request);
    }

    /**
     * Consulta que usa orders-service al crear la orden. Incluye nombre y fecha
     * del evento para que orders guarde el snapshot sin depender de catalog.
     */
    @GetMapping("/reservations/{reservaId}")
    public ReservationResponse obtenerReserva(@PathVariable UUID reservaId) {
        return reservaService.obtenerInterno(reservaId);
    }

    @PostMapping("/reservations/{reservaId}/confirm")
    public ReservationResponse confirmar(@PathVariable UUID reservaId) {
        return confirmacionService.confirmar(reservaId);
    }

    @PostMapping("/reservations/{reservaId}/release")
    public ResponseEntity<Void> liberar(@PathVariable UUID reservaId,
                                        @RequestBody(required = false) ReleaseRequest request) {
        confirmacionService.liberar(reservaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/returns")
    public ResponseEntity<Void> devolver(
            @RequestHeader(value = "Idempotency-Key", required = false) String clave,
            @Valid @RequestBody ReturnRequest request) {
        confirmacionService.devolver(clave, request);
        return ResponseEntity.noContent().build();
    }
}
