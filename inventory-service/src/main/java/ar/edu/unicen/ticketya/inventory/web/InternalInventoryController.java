package ar.edu.unicen.ticketya.inventory.web;

import ar.edu.unicen.ticketya.inventory.servicio.InventarioService;
import ar.edu.unicen.ticketya.inventory.servicio.ReservaService;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalInventoryController {

    private final InventarioService inventarioService;
    private final ReservaService reservaService;

    public InternalInventoryController(InventarioService inventarioService,
                                       ReservaService reservaService) {
        this.inventarioService = inventarioService;
        this.reservaService = reservaService;
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
}
