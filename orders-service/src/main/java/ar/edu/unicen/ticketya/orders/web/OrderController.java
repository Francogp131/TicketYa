package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.servicio.OrderService;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> crear(
            @Valid @RequestBody CrearOrdenRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        OrderResponse response = orderService.crear(request, identity.userId(), idempotencyKey);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }

    @GetMapping("/{ordenId}")
    public OrderResponse obtener(
            @PathVariable UUID ordenId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        return orderService.obtener(ordenId, identity.userId());
    }
}
