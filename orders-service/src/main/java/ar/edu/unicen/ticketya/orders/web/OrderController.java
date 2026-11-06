package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.servicio.OrderQueryService;
import ar.edu.unicen.ticketya.orders.servicio.OrderService;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import ar.edu.unicen.ticketya.orders.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Órdenes", description = "Compra, pago mock, entradas y consultas propias")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderService orderService, OrderQueryService orderQueryService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    @Operation(summary = "Crear la orden, cobrar y emitir entradas",
            description = "Requiere Idempotency-Key. Token APROBADO, RECHAZADO o LENTO. El total se calcula en el servidor.")
    @ApiResponse(responseCode = "201", description = "Orden creada (PAGADA o RECHAZADA)")
    @ApiResponse(responseCode = "400", description = "Validación (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "403", description = "La reserva es de otro cliente (NOT_RESERVATION_OWNER)")
    @ApiResponse(responseCode = "404", description = "La reserva no existe (RESERVATION_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Idempotencia en proceso (IDEMPOTENCY_IN_PROGRESS)")
    @ApiResponse(responseCode = "410", description = "Reserva vencida (RESERVATION_EXPIRED)")
    @ApiResponse(responseCode = "422", description = "Clave reusada con otro cuerpo (IDEMPOTENCY_KEY_REUSED)")
    @ApiResponse(responseCode = "502", description = "Pasarela o inventory no disponible")
    public ResponseEntity<OrderResponse> crear(
            @Valid @RequestBody CrearOrdenRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        OrderResponse response = orderService.crear(request, identity.userId(), idempotencyKey);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar mis órdenes",
            description = "Paginado, más recientes primero. Solo las órdenes del cliente autenticado.")
    @ApiResponse(responseCode = "200", description = "Página de órdenes del cliente")
    @ApiResponse(responseCode = "400", description = "page/size inválidos (VALIDATION_ERROR)")
    public PageResponse<OrderResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        return orderQueryService.ordenes(identity.userId(), page, size);
    }

    @GetMapping("/{ordenId}")
    @Operation(summary = "Detalle de una orden", description = "Solo el dueño de la orden.")
    @ApiResponse(responseCode = "200", description = "Orden con items y entradas")
    @ApiResponse(responseCode = "403", description = "La orden es de otro cliente (ORDER_NOT_OWNER)")
    @ApiResponse(responseCode = "404", description = "La orden no existe (ORDER_NOT_FOUND)")
    public OrderResponse obtener(
            @PathVariable UUID ordenId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        return orderService.obtener(ordenId, identity.userId());
    }
}
