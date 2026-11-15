package ar.edu.unicen.ticketya.recommendation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.unicen.ticketya.recommendation.config.OpenApiConfig;
import ar.edu.unicen.ticketya.recommendation.servicio.PreferenceService;
import ar.edu.unicen.ticketya.recommendation.servicio.RecommendationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exporta el OpenAPI generado por springdoc a openapi/recommendation-openapi.json
 * (relativo a la raíz del repositorio, un nivel arriba de recommendation-service).
 */
@WebMvcTest(controllers = {PreferenceController.class, InternalPreferenceController.class,
        RecommendationController.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@Import({OpenApiConfig.class, InternalToken.class})
@TestPropertySource(properties = "recommendation.internal-token=test-internal")
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenceService preferenceService;
    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void exportaElOpenApiDeRecommendation() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode documento = new ObjectMapper().readTree(json);
        assertThat(documento.at("/paths").size()).isEqualTo(3);
        assertThat(documento.at("/components/schemas/ProblemDetail").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/bearerAuth").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-User-Id").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-User-Role").isMissingNode()).isFalse();
        assertThat(documento.at("/components/securitySchemes/X-Internal-Token").isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1recommendations/get").isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1recommendations~1preferences/put").isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1internal~1preferences~1{clienteId}/get").isMissingNode())
                .isFalse();
        assertThat(documento.at("/paths/~1api~1recommendations~1preferences/put/responses/400/content/"
                        + "application~1problem+json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ProblemDetail");

        Path destino = Path.of("..", "openapi", "recommendation-openapi.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, json, StandardCharsets.UTF_8);
    }
}
