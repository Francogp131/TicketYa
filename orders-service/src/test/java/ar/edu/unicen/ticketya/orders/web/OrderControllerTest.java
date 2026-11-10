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
import ar.edu.unicen.ticketya.orders.servicio.OrderQueryService;
import ar.edu.unicen.ticketya.orders.servicio.OrderService;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import ar.edu.unicen.ticketya.orders.web.dto.PageResponse;
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

    @MockitoBean
    private OrderQueryService orderQueryService;

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

    @Test
    void crearConReservaAjenaDevuelve403() throws Exception {
        when(orderService.crear(any(CrearOrdenRequest.class), any(), eq("compra-0001")))
                .thenThrow(ApiException.forbidden("NOT_RESERVATION_OWNER",
                        "La reserva pertenece a otro cliente"));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "compra-0001")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId":"11111111-1111-4111-8111-111111111111",
                                 "medioPago":"TARJETA","token":"APROBADO"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_RESERVATION_OWNER"));
    }

    @Test
    void crearConReservaInexistenteDevuelve404() throws Exception {
        when(orderService.crear(any(CrearOrdenRequest.class), any(), eq("compra-0001")))
                .thenThrow(ApiException.notFound("RESERVATION_NOT_FOUND",
                        "No existe la reserva 11111111-1111-4111-8111-111111111111"));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "compra-0001")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId":"11111111-1111-4111-8111-111111111111",
                                 "medioPago":"TARJETA","token":"APROBADO"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void crearConReservaVencidaDevuelve410() throws Exception {
        when(orderService.crear(any(CrearOrdenRequest.class), any(), eq("compra-0001")))
                .thenThrow(ApiException.gone("RESERVATION_EXPIRED",
                        "La reserva está vencida y no se puede pagar"));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "compra-0001")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId":"11111111-1111-4111-8111-111111111111",
                                 "medioPago":"TARJETA","token":"APROBADO"}
                                """))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
    }

    @Test
    void crearConClaveReusadaDevuelve422() throws Exception {
        when(orderService.crear(any(CrearOrdenRequest.class), any(), eq("compra-0001")))
                .thenThrow(ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                        "La Idempotency-Key ya se usó con un cuerpo distinto"));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "compra-0001")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservaId":"22222222-2222-4222-8222-222222222222",
                                 "medioPago":"TARJETA","token":"APROBADO"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void listarDevuelveLaPaginaDelCliente() throws Exception {
        OrderResponse orden = new OrderResponse(
                UUID.randomUUID(), UUID.fromString(CLIENTE), UUID.randomUUID(), UUID.randomUUID(),
                "Recital", OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                EstadoOrden.PAGADA, MedioPago.TARJETA, new BigDecimal("15000.00"),
                List.of(new OrderResponse.Item(UUID.randomUUID(), UUID.randomUUID(), 1,
                        new BigDecimal("15000.00"), new BigDecimal("15000.00"))),
                List.of(),
                OffsetDateTime.parse("2026-11-03T12:00:00-03:00"));
        when(orderQueryService.ordenes(eq(UUID.fromString(CLIENTE)), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(orden), 0, 20, 1, 1));

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orden.id().toString()))
                .andExpect(jsonPath("$.content[0].estado").value("PAGADA"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void obtenerOrdenAjenaDevuelve403() throws Exception {
        UUID ordenId = UUID.randomUUID();
        when(orderService.obtener(ordenId, UUID.fromString(CLIENTE)))
                .thenThrow(ApiException.forbidden("ORDER_NOT_OWNER",
                        "La orden pertenece a otro cliente"));

        mockMvc.perform(get("/api/orders/{ordenId}", ordenId)
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OWNER"));
    }

    @Test
    void listarConPageInvalidaDevuelve400() throws Exception {
        when(orderQueryService.ordenes(eq(UUID.fromString(CLIENTE)), eq(-1), eq(20)))
                .thenThrow(ApiException.badRequest("VALIDATION_ERROR",
                        "page debe ser mayor o igual a 0"));

        mockMvc.perform(get("/api/orders")
                        .param("page", "-1")
                        .param("size", "20")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
