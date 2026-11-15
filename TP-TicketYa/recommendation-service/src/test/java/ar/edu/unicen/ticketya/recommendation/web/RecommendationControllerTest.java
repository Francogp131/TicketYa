package ar.edu.unicen.ticketya.recommendation.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.recommendation.servicio.RecommendationService;
import ar.edu.unicen.ticketya.recommendation.web.dto.RecomendacionItem;
import ar.edu.unicen.ticketya.recommendation.web.dto.RecomendacionesResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    private static final UUID CLIENTE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String CLIENTE_TEXTO = CLIENTE.toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void sinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void conRolOrganizadorDevuelve403() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void devuelveLasRecomendacionesDelCliente() throws Exception {
        when(recommendationService.recomendar(CLIENTE)).thenReturn(new RecomendacionesResponse(
                CLIENTE_TEXTO,
                Instant.parse("2026-11-15T12:00:00Z"),
                List.of(new RecomendacionItem("e1", "Festival", "FESTIVAL", List.of("Los Piojos"),
                        "Tandil", Instant.parse("2027-03-15T00:00:00Z"), false, 9,
                        List.of("Coincide con tu tipo favorito: FESTIVAL")))));

        mockMvc.perform(get("/api/recommendations")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(CLIENTE_TEXTO))
                .andExpect(jsonPath("$.recomendaciones[0].eventoId").value("e1"))
                .andExpect(jsonPath("$.recomendaciones[0].motivos[0]")
                        .value("Coincide con tu tipo favorito: FESTIVAL"));
    }

    @Test
    void sinCandidatosDevuelveListaVacia() throws Exception {
        when(recommendationService.recomendar(CLIENTE)).thenReturn(new RecomendacionesResponse(
                CLIENTE_TEXTO, Instant.parse("2026-11-15T12:00:00Z"), List.of()));

        mockMvc.perform(get("/api/recommendations")
                        .header("X-User-Id", CLIENTE_TEXTO)
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recomendaciones").isEmpty());
    }
}
