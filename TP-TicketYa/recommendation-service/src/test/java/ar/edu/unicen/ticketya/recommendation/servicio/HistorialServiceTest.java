package ar.edu.unicen.ticketya.recommendation.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import ar.edu.unicen.ticketya.recommendation.evento.OrdenPagadaEvent;
import ar.edu.unicen.ticketya.recommendation.evento.OrdenPagadaPayload;
import ar.edu.unicen.ticketya.recommendation.repositorio.HistorialCompraRepository;
import com.mongodb.client.result.UpdateResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class HistorialServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-15T12:00:00Z");
    private static final String CLIENTE = "33333333-3333-4333-8333-333333333333";
    private static final String EVENTO = "d0000000-0000-4000-8000-000000000001";
    private static final String ORDEN = "f0000000-0000-4000-8000-000000000001";
    private static final String EVENT_ID = "e0000000-0000-4000-8000-000000000001";

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private HistorialCompraRepository historialRepository;

    private HistorialService historialService;

    @BeforeEach
    void setUp() {
        historialService = new HistorialService(mongoTemplate, historialRepository,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void registraUnaCompraPagadaConUpsertIdempotente() {
        UpdateResult resultado = mock(UpdateResult.class);
        when(resultado.getMatchedCount()).thenReturn(0L);
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(HistorialCompra.class)))
                .thenReturn(resultado);

        boolean creado = historialService.registrar(evento("orden.pagada", "PAGADA", EVENT_ID));

        assertThat(creado).isTrue();
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(HistorialCompra.class));
    }

    @Test
    void unEventoDuplicadoNoSeRegistraDeNuevo() {
        UpdateResult resultado = mock(UpdateResult.class);
        when(resultado.getMatchedCount()).thenReturn(1L);
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(HistorialCompra.class)))
                .thenReturn(resultado);

        boolean creado = historialService.registrar(evento("orden.pagada", "PAGADA", EVENT_ID));

        assertThat(creado).isFalse();
    }

    @Test
    void ignoraEventosQueNoSonOrdenPagada() {
        boolean creado = historialService.registrar(evento("orden.reintegrada", "PAGADA", EVENT_ID));

        assertThat(creado).isFalse();
        verify(mongoTemplate, never()).upsert(any(), any(), eq(HistorialCompra.class));
    }

    @Test
    void noRegistraUnaOrdenQueNoEstaPagada() {
        boolean creado = historialService.registrar(evento("orden.pagada", "RECHAZADA", EVENT_ID));

        assertThat(creado).isFalse();
        verify(mongoTemplate, never()).upsert(any(), any(), eq(HistorialCompra.class));
    }

    @Test
    void rechazaUnEventoSinEventoId() {
        assertThatThrownBy(() -> historialService.registrar(evento("orden.pagada", "PAGADA", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");

        verify(mongoTemplate, never()).upsert(any(), any(), eq(HistorialCompra.class));
    }

    @Test
    void consultaElHistorialPropio() {
        HistorialCompra compra = new HistorialCompra();
        compra.setEventoId(EVENTO);
        when(historialRepository.findByClienteId(CLIENTE)).thenReturn(List.of(compra));

        assertThat(historialService.historial(UUID.fromString(CLIENTE))).containsExactly(compra);
    }

    private OrdenPagadaEvent evento(String tipo, String estado, String eventId) {
        OrdenPagadaPayload payload = new OrdenPagadaPayload(
                ORDEN, CLIENTE, EVENTO, "Festival de Rock", "FESTIVAL",
                List.of("Los Piojos"), "Tandil", Instant.parse("2027-03-15T00:00:00Z"), estado);
        return new OrdenPagadaEvent(eventId, tipo, AHORA, UUID.randomUUID().toString(), 1, payload);
    }
}
