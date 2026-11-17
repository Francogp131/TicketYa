package ar.edu.unicen.ticketya.notification.web;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import ar.edu.unicen.ticketya.notification.servicio.ConsultaNotificacionService;
import ar.edu.unicen.ticketya.notification.web.dto.NotificacionResponse;
import ar.edu.unicen.ticketya.notification.web.dto.PageResponse;
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
@RequestMapping("/api/notifications")
@Tag(name = "Notificaciones", description = "Consulta de las notificaciones propias del cliente")
public class NotificacionController {

    private final ConsultaNotificacionService consultaService;

    public NotificacionController(ConsultaNotificacionService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping
    @Operation(summary = "Listar mis notificaciones",
            description = "Devuelve solo las notificaciones del cliente autenticado, ordenadas por fecha "
                    + "descendente. Permite filtrar por estado y pagina con page/size.")
    @ApiResponse(responseCode = "200", description = "Página de notificaciones propias")
    @ApiResponse(responseCode = "400", description = "Estado o paginación inválidos (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "El rol no es CLIENT (FORBIDDEN)")
    public PageResponse<NotificacionResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) EstadoNotificacion estado,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("CLIENT");
        return consultaService.listar(identity.userId(), estado, page, size);
    }

    @GetMapping("/{notificacionId}")
    @Operation(summary = "Consultar una notificación propia",
            description = "Devuelve el estado, los intentos y las fechas de una notificación del cliente "
                    + "autenticado. Una notificación de otro cliente responde 403.")
    @ApiResponse(responseCode = "200", description = "Notificación propia")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Notificación de otro cliente (NOTIFICATION_FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "La notificación no existe (NOTIFICATION_NOT_FOUND)")
    public NotificacionResponse obtener(
            @PathVariable UUID notificacionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("CLIENT");
        return consultaService.obtener(notificacionId, identity.userId());
    }
}
