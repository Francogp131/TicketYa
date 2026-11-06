package ar.edu.unicen.ticketya.catalog.web;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalEventController.class)
class InternalEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void sincronizaLaDisponibilidadYDevuelve204() throws Exception {
        UUID eventoId = UUID.randomUUID();

        mockMvc.perform(patch("/internal/events/{id}/availability", eventoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agotado\":true}"))
                .andExpect(status().isNoContent());

        verify(eventService).actualizarDisponibilidad(eventoId, true);
    }

    @Test
    void unEventoInexistenteDevuelve404() throws Exception {
        UUID eventoId = UUID.randomUUID();
        doThrow(ApiException.notFound("EVENT_NOT_FOUND", "No existe el evento"))
                .when(eventService).actualizarDisponibilidad(eq(eventoId), anyBoolean());

        mockMvc.perform(patch("/internal/events/{id}/availability", eventoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agotado\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void unCuerpoSinAgotadoDevuelve400() throws Exception {
        mockMvc.perform(patch("/internal/events/{id}/availability", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
