package ar.edu.unicen.ticketya.catalog.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.catalog.config.OpenApiConfig;
import ar.edu.unicen.ticketya.catalog.servicio.EventService;
import ar.edu.unicen.ticketya.catalog.servicio.VenueService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exporta el OpenAPI generado por springdoc a openapi/catalog-openapi.json
 * (relativo a la raíz del repositorio, un nivel arriba de catalog-service).
 */
@WebMvcTest(controllers = {VenueController.class, EventController.class, InternalEventController.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@Import(OpenApiConfig.class)
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;
    @MockitoBean
    private EventService eventService;

    @Test
    void exportaElOpenApiDeCatalog() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Path destino = Path.of("..", "openapi", "catalog-openapi.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, json, StandardCharsets.UTF_8);
    }
}
