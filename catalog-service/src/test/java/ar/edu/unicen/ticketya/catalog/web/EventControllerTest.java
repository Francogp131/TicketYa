package ar.edu.unicen.ticketya.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.web.dto.CancelacionResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearEventoRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoBusquedaResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoDetalleResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PageResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PrecioRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.PrecioResponse;
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

@WebMvcTest(EventController.class)
class EventControllerTest {

    private static final String ADMIN = "11111111-1111-4111-8111-111111111111";
    private static final String CLIENT = "33333333-3333-4333-8333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void cancelarSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(post("/api/catalog/events/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void cancelarConRolClienteDevuelve403() throws Exception {
        mockMvc.perform(post("/api/catalog/events/{id}/cancel", UUID.randomUUID())
                        .header("X-User-Id", CLIENT)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminPuedeCancelarYRecibeElEstadoDeLaNotificacion() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(eventService.cancelar(eventoId))
                .thenReturn(new CancelacionResponse(eventoId, EstadoEvento.CANCELADO, true));

        mockMvc.perform(post("/api/catalog/events/{id}/cancel", eventoId)
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(eventoId.toString()))
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.cancelacionPendiente").value(true));
    }

    @Test
    void buscarReenviaLosFiltrosYDevuelveLaPagina() throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        OffsetDateTime desde = OffsetDateTime.parse("2027-01-01T00:00:00-03:00");
        OffsetDateTime hasta = OffsetDateTime.parse("2027-12-31T23:59:59-03:00");
        EventoBusquedaResponse evento = new EventoBusquedaResponse(eventoId, "Festival",
                TipoEvento.FESTIVAL, "Tandil", "Estadio Municipal",
                OffsetDateTime.parse("2027-03-14T21:00:00-03:00"),
                EstadoEvento.PUBLICADO, new BigDecimal("18000"));
        when(eventService.buscar(eq(TipoEvento.FESTIVAL), eq(organizadorId), eq("Los Piojos"),
                eq("Tandil"), eq(desde), eq(hasta), eq("FECHA_DESC"), eq(1), eq(10)))
                .thenReturn(new PageResponse<>(List.of(evento), 1, 10, 1, 1));

        mockMvc.perform(get("/api/catalog/events")
                        .param("tipo", "FESTIVAL")
                        .param("organizadorId", organizadorId.toString())
                        .param("participante", "Los Piojos")
                        .param("ciudad", "Tandil")
                        .param("fechaDesde", desde.toString())
                        .param("fechaHasta", hasta.toString())
                        .param("orden", "FECHA_DESC")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(eventoId.toString()))
                .andExpect(jsonPath("$.content[0].ciudad").value("Tandil"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(eventService).buscar(TipoEvento.FESTIVAL, organizadorId, "Los Piojos", "Tandil",
                desde, hasta, "FECHA_DESC", 1, 10);
    }

    @Test
    void buscarSinResultadosDevuelve200ConListaVacia() throws Exception {
        when(eventService.buscar(any(), any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/catalog/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rangoDeFechasInvalidoDevuelve400() throws Exception {
        when(eventService.buscar(any(), any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(ApiException.badRequest("INVALID_DATE_RANGE", "rango inválido"));

        mockMvc.perform(get("/api/catalog/events")
                        .param("fechaDesde", "2027-12-31T00:00:00-03:00")
                        .param("fechaHasta", "2027-01-01T00:00:00-03:00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void ordenInvalidoDevuelve400() throws Exception {
        when(eventService.buscar(any(), any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(ApiException.badRequest("INVALID_SORT", "orden inválido"));

        mockMvc.perform(get("/api/catalog/events").param("orden", "PRECIO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void crearEventoMapeaElCuerpoYDevuelve201ConLocation() throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID lugarId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        OffsetDateTime fecha = OffsetDateTime.parse("2027-05-01T21:00:00-03:00");
        when(eventService.crearEvento(any(CrearEventoRequest.class), eq(organizadorId)))
                .thenReturn(new EventoResponse(eventoId, "Festival", null, TipoEvento.FESTIVAL,
                        lugarId, "Estadio", "Tandil", organizadorId, fecha, EstadoEvento.BORRADOR,
                        4, List.of("Artista A"), List.of()));

        mockMvc.perform(post("/api/catalog/events")
                        .header("X-User-Id", organizadorId.toString())
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Festival","tipo":"FESTIVAL","lugarId":"%s",
                                 "fecha":"2027-05-01T21:00:00-03:00","participantes":["Artista A"]}
                                """.formatted(lugarId)))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(eventoId.toString())))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        verify(eventService).crearEvento(any(CrearEventoRequest.class), eq(organizadorId));
    }

    @Test
    void asignarPrecioMapeaPathYBodyYDevuelve200() throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        when(eventService.asignarPrecio(eq(eventoId), eq(sectorId), any(PrecioRequest.class), eq(organizadorId)))
                .thenReturn(new PrecioResponse(eventoId, sectorId, new BigDecimal("15000")));

        mockMvc.perform(put("/api/catalog/events/{eventoId}/prices/{sectorId}", eventoId, sectorId)
                        .header("X-User-Id", organizadorId.toString())
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precio\":15000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(eventoId.toString()))
                .andExpect(jsonPath("$.sectorId").value(sectorId.toString()));

        verify(eventService).asignarPrecio(eq(eventoId), eq(sectorId), any(PrecioRequest.class), eq(organizadorId));
    }

    @Test
    void publicarMapeaElEventoYDevuelve200() throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        when(eventService.publicar(eventoId, organizadorId))
                .thenReturn(new EventoResponse(eventoId, "Festival", null, TipoEvento.FESTIVAL,
                        UUID.randomUUID(), "Estadio", "Tandil", organizadorId,
                        OffsetDateTime.parse("2027-05-01T21:00:00-03:00"), EstadoEvento.PUBLICADO,
                        4, List.of("Artista A"), List.of()));

        mockMvc.perform(post("/api/catalog/events/{id}/publish", eventoId)
                        .header("X-User-Id", organizadorId.toString())
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventoId.toString()))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));
    }

    @Test
    void detalleDevuelveElEventoConSusSectores() throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        when(eventService.detalle(eventoId))
                .thenReturn(new EventoDetalleResponse(eventoId, "Festival", null, TipoEvento.FESTIVAL,
                        UUID.randomUUID(), "Estadio", "Tandil", organizadorId,
                        OffsetDateTime.parse("2027-05-01T21:00:00-03:00"), EstadoEvento.PUBLICADO,
                        4, List.of("Artista A"), List.of()));

        mockMvc.perform(get("/api/catalog/events/{id}", eventoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventoId.toString()))
                .andExpect(jsonPath("$.sectores").isArray());
    }

    @Test
    void tipoDeEventoInvalidoDevuelveValidationError() throws Exception {
        mockMvc.perform(get("/api/catalog/events").param("tipo", "NO_EXISTE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("tipo"));
    }
}
