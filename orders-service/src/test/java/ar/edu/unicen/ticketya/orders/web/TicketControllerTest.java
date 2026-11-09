package ar.edu.unicen.ticketya.orders.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.orders.servicio.OrderQueryService;
import ar.edu.unicen.ticketya.orders.web.dto.PageResponse;
import ar.edu.unicen.ticketya.orders.web.dto.TicketResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketController.class)
class TicketControllerTest {

    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    void listarSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(get("/api/orders/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listarDevuelveLaPaginaDelCliente() throws Exception {
        TicketResponse ticket = new TicketResponse(UUID.randomUUID(), UUID.randomUUID(),
                "Recital", OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "VALIDA",
                OffsetDateTime.parse("2026-11-06T12:00:00-03:00"));
        when(orderQueryService.entradas(eq(UUID.fromString(CLIENTE)), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(ticket), 0, 20, 1, 1));

        mockMvc.perform(get("/api/orders/tickets")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ticket.id().toString()))
                .andExpect(jsonPath("$.content[0].estado").value("VALIDA"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void obtenerEntradaAjenaDevuelve403() throws Exception {
        UUID entradaId = UUID.randomUUID();
        when(orderQueryService.entrada(entradaId, UUID.fromString(CLIENTE)))
                .thenThrow(ApiException.forbidden("NOT_TICKET_OWNER", "La entrada pertenece a otro cliente"));

        mockMvc.perform(get("/api/orders/tickets/{entradaId}", entradaId)
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TICKET_OWNER"));
    }

    @Test
    void listarConEventoYPaginacion() throws Exception {
        UUID eventoId = UUID.randomUUID();
        TicketResponse ticket = new TicketResponse(UUID.randomUUID(), eventoId,
                "Recital", OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "VALIDA",
                OffsetDateTime.parse("2026-11-06T12:00:00-03:00"));
        when(orderQueryService.entradas(eq(UUID.fromString(CLIENTE)), eq(eventoId), eq(1), eq(5)))
                .thenReturn(new PageResponse<>(List.of(ticket), 1, 5, 6, 2));

        mockMvc.perform(get("/api/orders/tickets")
                        .param("eventoId", eventoId.toString())
                        .param("page", "1")
                        .param("size", "5")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ticket.id().toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void obtenerEntradaInexistenteDevuelve404() throws Exception {
        UUID entradaId = UUID.randomUUID();
        when(orderQueryService.entrada(entradaId, UUID.fromString(CLIENTE)))
                .thenThrow(ApiException.notFound("TICKET_NOT_FOUND", "No existe la entrada " + entradaId));

        mockMvc.perform(get("/api/orders/tickets/{entradaId}", entradaId)
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }
}
