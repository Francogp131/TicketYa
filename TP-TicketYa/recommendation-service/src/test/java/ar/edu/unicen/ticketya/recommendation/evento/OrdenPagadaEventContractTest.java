package ar.edu.unicen.ticketya.recommendation.evento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Contrato compartido: el fixture documentado en docs/ejemplos/orden-pagada.json
 * debe deserializarse con el mismo record que usa el listener.
 */
class OrdenPagadaEventContractTest {

    @Test
    void elFixtureDocumentadoSeDeserializaConElConsumidor() throws Exception {
        Path fixture = Path.of("..", "docs", "ejemplos", "orden-pagada.json");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        OrdenPagadaEvent event = mapper.readValue(fixture.toFile(), OrdenPagadaEvent.class);

        assertThat(event.type()).isEqualTo(OrdenPagadaEvent.TIPO);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.payload()).isNotNull();
        assertThat(event.payload().clienteId()).isEqualTo("33333333-3333-4333-8333-333333333333");
        assertThat(event.payload().eventoTipo()).isEqualTo("FESTIVAL");
        assertThat(event.payload().artistas()).contains("Los Piojos", "Callejeros");
        assertThat(event.payload().ciudad()).isEqualTo("Tandil");
        assertThat(event.payload().estado()).isEqualTo(OrdenPagadaPayload.ESTADO_PAGADA);
    }
}
