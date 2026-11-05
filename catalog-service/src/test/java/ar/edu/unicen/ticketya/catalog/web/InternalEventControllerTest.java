package ar.edu.unicen.ticketya.catalog.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
    void elCallbackDeAgotadoMarcaElEvento() throws Exception {
        UUID eventoId = UUID.randomUUID();

        mockMvc.perform(patch("/internal/events/{id}/availability", eventoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agotado\":true}"))
                .andExpect(status().isNoContent());

        verify(eventService).actualizarDisponibilidad(eventoId, true);
    }

    @Test
    void elCallbackSinAgotadoDevuelve400() throws Exception {
        mockMvc.perform(patch("/internal/events/{id}/availability", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(eventService, never()).actualizarDisponibilidad(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
