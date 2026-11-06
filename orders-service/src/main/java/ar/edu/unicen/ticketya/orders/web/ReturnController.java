package ar.edu.unicen.ticketya.orders.web;

import ar.edu.unicen.ticketya.orders.servicio.DevolucionService;
import ar.edu.unicen.ticketya.orders.web.dto.ReturnRequest;
import ar.edu.unicen.ticketya.orders.web.dto.ReturnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Devoluciones", description = "Solicitud, aprobación y rechazo de devoluciones")
public class ReturnController {

    private final DevolucionService devolucionService;

    public ReturnController(DevolucionService devolucionService) {
        this.devolucionService = devolucionService;
    }

    @PostMapping("/tickets/{entradaId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicitar la devolución de una entrada",
            description = "Solo el dueño, entrada VALIDA y más de 72 horas antes del evento.")
    @ApiResponse(responseCode = "201", description = "Solicitud creada en PENDIENTE")
    @ApiResponse(responseCode = "403", description = "La entrada es de otro cliente (NOT_TICKET_OWNER)")
    @ApiResponse(responseCode = "404", description = "La entrada no existe (TICKET_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Entrada no válida (TICKET_NOT_VALID) o devolución abierta (RETURN_ALREADY_EXISTS)")
    @ApiResponse(responseCode = "422", description = "Fuera del plazo de 72 horas (RETURN_WINDOW_CLOSED)")
    public ReturnResponse solicitar(
            @PathVariable UUID entradaId,
            @Valid @RequestBody ReturnRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        return devolucionService.solicitar(entradaId, identity.userId(), request.motivo());
    }

    @PostMapping("/returns/{devolucionId}/approve")
    @Operation(summary = "Aprobar una devolución",
            description = "Requiere ADMIN. Libera el lugar, invalida la entrada y solicita el reintegro.")
    @ApiResponse(responseCode = "200", description = "Devolución APROBADA")
    @ApiResponse(responseCode = "404", description = "La devolución no existe (RETURN_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "La devolución ya fue resuelta (RETURN_ALREADY_RESOLVED)")
    @ApiResponse(responseCode = "502", description = "Inventory no pudo liberar; queda PROCESANDO (INVENTORY_UNAVAILABLE)")
    public ReturnResponse aprobar(
            @PathVariable UUID devolucionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("ADMIN");
        return devolucionService.aprobar(devolucionId);
    }

    @PostMapping("/returns/{devolucionId}/reject")
    @Operation(summary = "Rechazar una devolución",
            description = "Requiere ADMIN. La entrada sigue VALIDA.")
    @ApiResponse(responseCode = "200", description = "Devolución RECHAZADA")
    @ApiResponse(responseCode = "404", description = "La devolución no existe (RETURN_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "La devolución ya fue resuelta (RETURN_ALREADY_RESOLVED)")
    public ReturnResponse rechazar(
            @PathVariable UUID devolucionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        RequestIdentity identity = RequestIdentity.require(userId, role);
        identity.requireRole("ADMIN");
        return devolucionService.rechazar(devolucionId);
    }
}
