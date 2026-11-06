package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.servicio.OrderService;
import ar.edu.unicen.ticketya.orders.web.dto.CancelacionEventoResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/events/{eventoId}/cancel")
    public CancelacionEventoResponse cancelar(@PathVariable UUID eventoId) {
        return orderService.cancelarEvento(eventoId);
    }
}
