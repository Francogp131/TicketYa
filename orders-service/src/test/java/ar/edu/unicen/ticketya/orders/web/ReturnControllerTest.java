package ar.edu.unicen.ticketya.orders.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.orders.dominio.EstadoDevolucion;
import ar.edu.unicen.ticketya.orders.servicio.DevolucionService;
import ar.edu.unicen.ticketya.orders.web.dto.ReturnResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReturnController.class)
class ReturnControllerTest {

    private static final String ADMIN = "11111111-1111-4111-8111-111111111111";
    private static final String CLIENT = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DevolucionService devolucionService;

    @Test
    void solicitarSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(post("/api/orders/tickets/{id}/returns", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"No puedo\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void aprobarConRolClienteDevuelve403() throws Exception {
        mockMvc.perform(post("/api/orders/returns/{id}/approve", UUID.randomUUID())
                        .header("X-User-Id", CLIENT)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminPuedeAprobarYRecibeElEstado() throws Exception {
        UUID devolucionId = UUID.randomUUID();
        when(devolucionService.aprobar(devolucionId))
                .thenReturn(new ReturnResponse(devolucionId, UUID.randomUUID(),
                        EstadoDevolucion.APROBADA, "No puedo",
                        OffsetDateTime.parse("2026-11-06T12:00:00-03:00"),
                        OffsetDateTime.parse("2026-11-06T12:05:00-03:00")));

        mockMvc.perform(post("/api/orders/returns/{id}/approve", devolucionId)
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(devolucionId.toString()))
                .andExpect(jsonPath("$.estado").value("APROBADA"));
    }

    @Test
    void solicitarDevolucionValidaDevuelve201() throws Exception {
        UUID entradaId = UUID.randomUUID();
        UUID devolucionId = UUID.randomUUID();
        when(devolucionService.solicitar(eq(entradaId), eq(UUID.fromString(CLIENT)),
                eq("No puedo")))
                .thenReturn(new ReturnResponse(devolucionId, entradaId,
                        EstadoDevolucion.PENDIENTE, "No puedo",
                        OffsetDateTime.parse("2026-11-06T12:00:00-03:00"),
                        null));

        mockMvc.perform(post("/api/orders/tickets/{entradaId}/returns", entradaId)
                        .header("X-User-Id", CLIENT)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"No puedo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(devolucionId.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void solicitarFueraDePlazoDevuelve422() throws Exception {
        UUID entradaId = UUID.randomUUID();
        when(devolucionService.solicitar(any(), any(), any()))
                .thenThrow(ApiException.unprocessable("RETURN_WINDOW_CLOSED",
                        "Solo se puede devolver hasta 72 horas antes del evento"));

        mockMvc.perform(post("/api/orders/tickets/{entradaId}/returns", entradaId)
                        .header("X-User-Id", CLIENT)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"No puedo\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RETURN_WINDOW_CLOSED"));
    }

    @Test
    void solicitarEntradaAjenaDevuelve403() throws Exception {
        UUID entradaId = UUID.randomUUID();
        when(devolucionService.solicitar(any(), any(), any()))
                .thenThrow(ApiException.forbidden("NOT_TICKET_OWNER",
                        "La entrada pertenece a otro cliente"));

        mockMvc.perform(post("/api/orders/tickets/{entradaId}/returns", entradaId)
                        .header("X-User-Id", CLIENT)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"No puedo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TICKET_OWNER"));
    }

    @Test
    void solicitarDevolucionDuplicadaDevuelve409() throws Exception {
        UUID entradaId = UUID.randomUUID();
        when(devolucionService.solicitar(any(), any(), any()))
                .thenThrow(ApiException.conflict("RETURN_ALREADY_EXISTS",
                        "Ya existe una devolucion abierta para esta entrada"));

        mockMvc.perform(post("/api/orders/tickets/{entradaId}/returns", entradaId)
                        .header("X-User-Id", CLIENT)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"No puedo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_ALREADY_EXISTS"));
    }
}
