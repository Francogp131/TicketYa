package ar.edu.unicen.ticketya.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.catalog.servicio.VenueService;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearLugarRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.LugarResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VenueController.class)
class VenueControllerTest {

    private static final String ADMIN = "11111111-1111-4111-8111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;

    @Test
    void crearLugarSinIdentidadDevuelve401() throws Exception {
        mockMvc.perform(post("/api/catalog/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Estadio","direccion":"Calle 1","ciudad":"Tandil",
                                 "latitud":-37.3,"longitud":-59.1}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void crearLugarConLatitudInvalidaDevuelveProblemDetail() throws Exception {
        mockMvc.perform(post("/api/catalog/venues")
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Estadio","direccion":"Calle 1","ciudad":"Tandil",
                                 "latitud":100,"longitud":-59.1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.instance").value("/api/catalog/venues"));
    }

    @Test
    void crearLugarConRolClienteDevuelve403() throws Exception {
        mockMvc.perform(post("/api/catalog/venues")
                        .header("X-User-Id", "33333333-3333-4333-8333-333333333333")
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Estadio","direccion":"Calle 1","ciudad":"Tandil",
                                 "latitud":-37.3,"longitud":-59.1}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void crearSectorDuplicadoDevuelve409() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(venueService.crearSector(any(), any()))
                .thenThrow(ApiException.conflict("SECTOR_ALREADY_EXISTS", "Ya existe un sector con ese nombre"));

        mockMvc.perform(post("/api/catalog/venues/{id}/sectors", venueId)
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Platea","tipo":"NUMERADO","filas":2,"asientosPorFila":5}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SECTOR_ALREADY_EXISTS"));
    }

    @Test
    void crearLugarValidoDevuelve201() throws Exception {
        UUID id = UUID.randomUUID();
        when(venueService.crearLugar(any(CrearLugarRequest.class)))
                .thenReturn(new LugarResponse(id, "Estadio", "Calle 1", "Tandil",
                        new BigDecimal("-37.3"), new BigDecimal("-59.1")));

        mockMvc.perform(post("/api/catalog/venues")
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Estadio","direccion":"Calle 1","ciudad":"Tandil",
                                 "latitud":-37.3,"longitud":-59.1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void lugarInexistenteDevuelve404() throws Exception {
        UUID id = UUID.randomUUID();
        when(venueService.obtenerLugar(id))
                .thenThrow(ApiException.notFound("VENUE_NOT_FOUND", "No existe"));

        mockMvc.perform(get("/api/catalog/venues/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VENUE_NOT_FOUND"));
    }
}
