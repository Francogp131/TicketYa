package ar.edu.unicen.ticketya.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoCandidatoResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoReporteResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.SectorReporteResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalCatalogController.class)
@Import(InternalToken.class)
@TestPropertySource(properties = "catalog.internal-token=test-internal-token")
class InternalCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void devuelveElReporteConTokenInterno() throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID organizadorId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        when(eventService.reporteEvento(eventoId)).thenReturn(new EventoReporteResponse(
                eventoId, "Festival", organizadorId, UUID.randomUUID(), "Estadio", "Tandil",
                OffsetDateTime.parse("2026-12-01T20:00:00-03:00"), EstadoEvento.PUBLICADO,
                List.of("Artista"),
                List.of(new SectorReporteResponse(sectorId, "Platea", TipoSector.NUMERADO, 100,
                        new BigDecimal("1500.00")))));

        mockMvc.perform(get("/internal/events/{id}/report", eventoId)
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(eventoId.toString()))
                .andExpect(jsonPath("$.organizadorId").value(organizadorId.toString()))
                .andExpect(jsonPath("$.sectores[0].sectorId").value(sectorId.toString()))
                .andExpect(jsonPath("$.sectores[0].capacidad").value(100))
                .andExpect(jsonPath("$.sectores[0].precio").value(1500.00));
    }

    @Test
    void devuelveCandidatosConLosFiltros() throws Exception {
        UUID eventoId = UUID.randomUUID();
        when(eventService.candidatos(eq(TipoEvento.RECITAL), eq("Soda"), eq("Tandil"),
                any(OffsetDateTime.class), any(OffsetDateTime.class), eq(5)))
                .thenReturn(List.of(new EventoCandidatoResponse(eventoId.toString(), "Recital", "RECITAL",
                        List.of("Soda Stereo"), "Tandil", Instant.parse("2026-12-01T23:00:00Z"),
                        "PUBLICADO", true)));

        mockMvc.perform(get("/internal/recommendations/candidates")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("tipo", "RECITAL")
                        .param("artista", "Soda")
                        .param("ciudad", "Tandil")
                        .param("fechaDesde", "2026-11-01T00:00:00-03:00")
                        .param("fechaHasta", "2026-12-31T00:00:00-03:00")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventoId").value(eventoId.toString()))
                .andExpect(jsonPath("$[0].artistas[0]").value("Soda Stereo"))
                .andExpect(jsonPath("$[0].altaDemanda").value(true));
    }

    @Test
    void elReporteSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/internal/events/{id}/report", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(eventService);
    }

    @Test
    void losCandidatosSinTokenDevuelven401() throws Exception {
        mockMvc.perform(get("/internal/recommendations/candidates"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(eventService, org.mockito.Mockito.never())
                .candidatos(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
