package ar.edu.unicen.ticketya.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiDocumentoTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elDocumentoIncluyeLosCodigosDelContrato() throws Exception {
        String cuerpo = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(cuerpo)
                .contains("/api/inventory/events/{eventoId}/availability")
                .contains("/api/inventory/events/{eventoId}/seats")
                .contains("/api/inventory/reservations")
                .contains("/internal/events/{eventoId}/inventory")
                .contains("/internal/reservations/{reservaId}/confirm")
                .contains("/internal/reservations/{reservaId}/release")
                .contains("/internal/returns")
                .contains("\"201\"")
                .contains("\"400\"")
                .contains("\"404\"")
                .contains("\"409\"")
                .contains("\"410\"")
                .contains("\"422\"")
                .contains("SEAT_NOT_AVAILABLE")
                .contains("INSUFFICIENT_AVAILABILITY")
                .contains("CLIENT_LIMIT_EXCEEDED")
                .contains("RESERVATION_EXPIRED");

        if (Boolean.getBoolean("export.openapi")) {
            ObjectMapper mapper = new ObjectMapper();
            String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(cuerpo));
            String normalizado = pretty.replace("\r\n", "\n") + "\n";
            Path destino = Path.of("..", "openapi", "inventory-openapi.json");
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, normalizado);
        }
    }
}
