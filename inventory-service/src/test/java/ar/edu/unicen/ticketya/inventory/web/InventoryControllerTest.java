package ar.edu.unicen.ticketya.inventory.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import ar.edu.unicen.ticketya.inventory.servicio.DisponibilidadService;
import ar.edu.unicen.ticketya.inventory.servicio.ReservaService;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
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

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    @MockitoBean
    private DisponibilidadService disponibilidadService;

    @Test
    void reservarSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventoId":"11111111-1111-4111-8111-111111111111",
                                 "items":[{"sectorId":"22222222-2222-4222-8222-222222222222",
                                           "asientoIds":["33333333-3333-4333-8333-333333333333"]}]}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void reservarDevuelve201ConLaReserva() throws Exception {
        UUID reservaId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        UUID asientoId = UUID.randomUUID();
        ReservationResponse response = new ReservationResponse(
                reservaId, eventoId, "Recital", OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                UUID.fromString(CLIENTE), EstadoReserva.VIGENTE,
                OffsetDateTime.parse("2026-11-03T12:10:00-03:00"),
                List.of(new ReservationResponse.Item(sectorId, asientoId, 1,
                        new BigDecimal("15000"), new BigDecimal("15000"))),
                new BigDecimal("15000"));
        when(reservaService.reservar(any(ReservaRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/inventory/reservations")
                        .header("X-User-Id", CLIENTE)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventoId":"%s",
                                 "items":[{"sectorId":"%s","asientoIds":["%s"]}]}
                                """.formatted(eventoId, sectorId, asientoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reservaId.toString()))
                .andExpect(jsonPath("$.total").value(15000));
    }
}
