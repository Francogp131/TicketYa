package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.servicio.OrderQueryService;
import ar.edu.unicen.ticketya.orders.servicio.OrderService;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import ar.edu.unicen.ticketya.orders.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @ApiResponse(responseCode = "201", description = "Orden creada (PAGADA o RECHAZADA)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class),
                    examples = {
                            @ExampleObject(name = "Orden aprobada", value = """
                                    {
                                      "id": "11111111-1111-4111-8111-111111111111",
                                      "clienteId": "33333333-3333-4333-8333-333333333333",
                                      "reservaId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                      "eventoId": "22222222-2222-4222-8222-222222222222",
                                      "eventoNombre": "Recital",
                                      "eventoFecha": "2027-01-15T21:00:00-03:00",
                                      "estado": "PAGADA",
                                      "medioPago": "TARJETA",
                                      "total": 15000.00,
                                      "items": [{"sectorId":"44444444-4444-4444-8444-444444444444","asientoId":"55555555-5555-4555-8555-555555555555","cantidad":1,"precioUnitario":15000.00,"subtotal":15000.00}],
                                      "entradas": [{"id":"66666666-6666-4666-8666-666666666666","qrCode":"77777777-7777-4777-8777-777777777777","estado":"VALIDA","sectorId":"44444444-4444-4444-8444-444444444444","asientoId":"55555555-5555-4555-8555-555555555555"}],
                                      "createdAt": "2026-11-10T12:00:00-03:00"
                                    }
                                    """),
                            @ExampleObject(name = "Orden rechazada", value = """
                                    {
                                      "id": "11111111-1111-4111-8111-111111111112",
                                      "clienteId": "33333333-3333-4333-8333-333333333333",
                                      "reservaId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                      "eventoId": "22222222-2222-4222-8222-222222222222",
                                      "eventoNombre": "Recital",
                                      "eventoFecha": "2027-01-15T21:00:00-03:00",
                                      "estado": "RECHAZADA",
                                      "medioPago": "TARJETA",
                                      "total": 15000.00,
                                      "items": [{"sectorId":"44444444-4444-4444-8444-444444444444","asientoId":"55555555-5555-4555-8555-555555555555","cantidad":1,"precioUnitario":15000.00,"subtotal":15000.00}],
                                      "entradas": [],
                                      "createdAt": "2026-11-10T12:00:00-03:00"
                                    }
                                    """)
                    }))
    @ApiResponse(responseCode = "400", description = "Validación (VALIDATION_ERROR)",
            content = @Content(mediaType = "application/problem+json", examples = @ExampleObject(value = """
                    {"type":"https://ticketya.local/errors/validation-error","title":"Bad Request","status":400,"detail":"La solicitud tiene campos inválidos","instance":"/api/orders","code":"VALIDATION_ERROR","errors":[{"field":"reservaId","message":"must not be null"}]}
                    """)))
    @ApiResponse(responseCode = "409", description = "Idempotencia en proceso (IDEMPOTENCY_IN_PROGRESS)",
            content = @Content(mediaType = "application/problem+json", examples = @ExampleObject(name = "Idempotencia en proceso", value = """
                    {"type":"https://ticketya.local/errors/idempotency-in-progress","title":"Conflict","status":409,"detail":"La compra con esta Idempotency-Key todavía está en proceso","instance":"/api/orders","code":"IDEMPOTENCY_IN_PROGRESS","errors":[]}
                    """)))
    @ApiResponse(responseCode = "422", description = "Clave reusada con otro cuerpo (IDEMPOTENCY_KEY_REUSED)",
            content = @Content(mediaType = "application/problem+json", examples = @ExampleObject(name = "Clave reusada", value = """
                    {"type":"https://ticketya.local/errors/idempotency-key-reused","title":"Unprocessable Entity","status":422,"detail":"La Idempotency-Key ya se usó con un cuerpo distinto","instance":"/api/orders","code":"IDEMPOTENCY_KEY_REUSED","errors":[]}
                    """)))
    @ApiResponse(responseCode = "403", description = "La reserva es de otro cliente (NOT_RESERVATION_OWNER)")
    @ApiResponse(responseCode = "404", description = "La reserva no existe (RESERVATION_NOT_FOUND)")
    @ApiResponse(responseCode = "410", description = "Reserva vencida (RESERVATION_EXPIRED)")
    @ApiResponse(responseCode = "502", description = "Pasarela o inventory no disponible",
            content = @Content(mediaType = "application/problem+json", examples = @ExampleObject(value = """
                    {"type":"https://ticketya.local/errors/payment-gateway-error","title":"Bad Gateway","status":502,"detail":"La pasarela de pagos falló o no respondió a tiempo","instance":"/api/orders","code":"PAYMENT_GATEWAY_ERROR","errors":[]}
                    """)))
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
