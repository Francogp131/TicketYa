package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.servicio.OrderQueryService;
import ar.edu.unicen.ticketya.orders.web.dto.PageResponse;
import ar.edu.unicen.ticketya.orders.web.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/tickets")
@Tag(name = "Entradas", description = "Consulta de entradas propias")
public class TicketController {

    private final OrderQueryService orderQueryService;

    public TicketController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping
    @Operation(summary = "Listar mis entradas",
            description = "Paginado, más recientes primero; opcionalmente filtrado por evento.")
    @ApiResponse(responseCode = "200", description = "Página de entradas del cliente")
    @ApiResponse(responseCode = "400", description = "page/size inválidos (VALIDATION_ERROR)")
    public PageResponse<TicketResponse> listar(
            @RequestParam(required = false) UUID eventoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        return orderQueryService.entradas(identity.userId(), eventoId, page, size);
    }

    @GetMapping("/{entradaId}")
    @Operation(summary = "Detalle de una entrada", description = "Solo el dueño de la entrada.")
    @ApiResponse(responseCode = "200", description = "Entrada con evento, sector, asiento y QR")
    @ApiResponse(responseCode = "403", description = "La entrada es de otro cliente (NOT_TICKET_OWNER)")
    @ApiResponse(responseCode = "404", description = "La entrada no existe (TICKET_NOT_FOUND)")
    public TicketResponse obtener(
            @PathVariable UUID entradaId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        return orderQueryService.entrada(entradaId, identity.userId());
    }
}
