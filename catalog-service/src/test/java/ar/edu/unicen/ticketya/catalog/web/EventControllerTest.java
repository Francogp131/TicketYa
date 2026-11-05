package ar.edu.unicen.ticketya.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoDetalleResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
class EventControllerTest {

    private static final String ORGANIZER = "22222222-2222-4222-8222-222222222222";
    private static final String CLIENT = "33333333-3333-4333-8333-333333333333";
    private static final String LUGAR_ID = "a0000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void crearEventoSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(post("/api/catalog/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventoJson("RECITAL", 4)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void crearEventoConRolClienteDevuelve403() throws Exception {
        mockMvc.perform(post("/api/catalog/events")
                        .header("X-User-Id", CLIENT)
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventoJson("RECITAL", 4)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void crearEventoConTipoInvalidoDevuelveValidationError() throws Exception {
        mockMvc.perform(post("/api/catalog/events")
                        .header("X-User-Id", ORGANIZER)
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventoJson("INVALIDO", 4)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("tipo"));
    }

    @Test
    void crearEventoConLimiteCeroDevuelveValidationError() throws Exception {
        mockMvc.perform(post("/api/catalog/events")
                        .header("X-User-Id", ORGANIZER)
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventoJson("RECITAL", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("limitePorCliente"));
    }

    @Test
    void crearEventoValidoDevuelve201EnBorrador() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(eventService.crearEvento(any(), any())).thenReturn(eventoResponse(eventoId));

        mockMvc.perform(post("/api/catalog/events")
                        .header("X-User-Id", ORGANIZER)
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventoJson("RECITAL", 4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(eventoId.toString()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.organizadorId").value(ORGANIZER));
    }

    @Test
    void detalleDeEventoEsPublicoSinIdentidad() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(eventService.detalle(eventoId)).thenReturn(eventoDetalleResponse(eventoId));

        mockMvc.perform(get("/api/catalog/events/{id}", eventoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventoId.toString()))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));
    }

    private String eventoJson(String tipo, int limitePorCliente) {
        return """
                {"nombre":"Recital","descripcion":"Una noche de rock","tipo":"%s","lugarId":"%s",
                 "fecha":"2027-01-15T21:00:00-03:00","participantes":["Artista"],
                 "limitePorCliente":%d}
                """.formatted(tipo, LUGAR_ID, limitePorCliente);
    }

    private EventoResponse eventoResponse(UUID eventoId) {
        return new EventoResponse(eventoId, "Recital", "Una noche de rock", TipoEvento.RECITAL,
                UUID.fromString(LUGAR_ID), "Estadio Municipal", "Tandil", UUID.fromString(ORGANIZER),
                OffsetDateTime.parse("2027-01-15T21:00:00-03:00"), EstadoEvento.BORRADOR, 4,
                List.of("Artista"), List.of());
    }

    private EventoDetalleResponse eventoDetalleResponse(UUID eventoId) {
        return new EventoDetalleResponse(eventoId, "Recital", "Una noche de rock", TipoEvento.RECITAL,
                UUID.fromString(LUGAR_ID), "Estadio Municipal", "Tandil", UUID.fromString(ORGANIZER),
                OffsetDateTime.parse("2027-01-15T21:00:00-03:00"), EstadoEvento.PUBLICADO, 4,
                List.of("Artista"), List.of());
    }
}
