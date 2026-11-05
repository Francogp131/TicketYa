package ar.edu.unicen.ticketya.orders.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import ar.edu.unicen.ticketya.orders.servicio.OrderService;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void crearSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "compra-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId":"11111111-1111-4111-8111-111111111111",
                                 "medioPago":"TARJETA","token":"APROBADO"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void obtenerOrdenInexistenteDevuelve404() throws Exception {
        UUID ordenId = UUID.randomUUID();
        when(orderService.obtener(ordenId, UUID.fromString(CLIENTE)))
                .thenThrow(ApiException.notFound("ORDER_NOT_FOUND", "No existe la orden " + ordenId));

        mockMvc.perform(get("/api/orders/{ordenId}", ordenId)
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void crearOrdenValidaDevuelve201() throws Exception {
        UUID reservaId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                ordenId, UUID.fromString(CLIENTE), reservaId, eventoId,
                "Recital", OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                EstadoOrden.PENDIENTE_PAGO, MedioPago.TARJETA, new BigDecimal("15000.00"),
                List.of(new OrderResponse.Item(UUID.randomUUID(), UUID.randomUUID(), 1,
                        new BigDecimal("15000.00"), new BigDecimal("15000.00"))),
                List.of(),
                OffsetDateTime.parse("2026-11-03T12:00:00-03:00"));
        when(orderService.crear(any(CrearOrdenRequest.class), any(), eq("compra-0001"))).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "compra-0001")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId":"%s","medioPago":"TARJETA","token":"APROBADO"}
                                """.formatted(reservaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ordenId.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"));
    }
}
