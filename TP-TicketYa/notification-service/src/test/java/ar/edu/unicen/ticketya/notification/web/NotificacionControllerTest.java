package ar.edu.unicen.ticketya.notification.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import ar.edu.unicen.ticketya.notification.servicio.ConsultaNotificacionService;
import ar.edu.unicen.ticketya.notification.web.dto.NotificacionResponse;
import ar.edu.unicen.ticketya.notification.web.dto.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificacionController.class)
class NotificacionControllerTest {

    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";
    private static final String ORGANIZADOR = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultaNotificacionService consultaService;

    @Test
    void listarSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listarConRolOrganizadorDevuelve403() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("X-User-Id", ORGANIZADOR)
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listarDevuelveEstadoIntentosYPaginacion() throws Exception {
        NotificacionResponse notificacion = new NotificacionResponse(
                UUID.randomUUID(), UUID.randomUUID(), "orden.pagada", "Compra confirmada", "Detalle",
                EstadoNotificacion.ENVIADA, 1, null,
                OffsetDateTime.parse("2026-11-15T12:00:00-03:00"),
                OffsetDateTime.parse("2026-11-15T12:00:01-03:00"));
        when(consultaService.listar(any(UUID.class), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(notificacion), 0, 20, 1, 1));

        mockMvc.perform(get("/api/notifications")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estado").value("ENVIADA"))
                .andExpect(jsonPath("$.content[0].intentos").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(consultaService).listar(UUID.fromString(CLIENTE), null, 0, 20);
    }

    @Test
    void listarReenviaElFiltroDeEstadoYLaPagina() throws Exception {
        when(consultaService.listar(any(UUID.class), eq(EstadoNotificacion.FALLIDA), eq(1), eq(5)))
                .thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/api/notifications")
                        .param("estado", "FALLIDA")
                        .param("page", "1")
                        .param("size", "5")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(consultaService).listar(UUID.fromString(CLIENTE), EstadoNotificacion.FALLIDA, 1, 5);
    }

    @Test
    void estadoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .param("estado", "DESCONOCIDO")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void detallePropioDevuelve200() throws Exception {
        UUID notificacionId = UUID.randomUUID();
        when(consultaService.obtener(notificacionId, UUID.fromString(CLIENTE)))
                .thenReturn(new NotificacionResponse(
                        notificacionId, UUID.randomUUID(), "evento.cancelado", "Evento cancelado",
                        "Se reintegrará tu entrada", EstadoNotificacion.PENDIENTE, 2,
                        OffsetDateTime.parse("2026-11-15T12:10:00-03:00"),
                        OffsetDateTime.parse("2026-11-15T12:00:00-03:00"), null));

        mockMvc.perform(get("/api/notifications/{id}", notificacionId)
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificacionId.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(2));
    }

    @Test
    void detalleAjenoDevuelve403() throws Exception {
        UUID notificacionId = UUID.randomUUID();
        when(consultaService.obtener(eq(notificacionId), any(UUID.class)))
                .thenThrow(ApiException.forbidden("NOTIFICATION_FORBIDDEN",
                        "La notificación pertenece a otro usuario"));

        mockMvc.perform(get("/api/notifications/{id}", notificacionId)
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_FORBIDDEN"));
    }
}
