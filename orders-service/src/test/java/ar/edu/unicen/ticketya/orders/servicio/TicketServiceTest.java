package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private EntradaRepository entradaRepository;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(entradaRepository);
    }

    @Test
    void emiteUnaEntradaPorAsientoYUnaPorUnidadDeZonaConQrUnico() {
        Orden orden = new Orden();
        orden.setId(UUID.randomUUID());
        orden.setClienteId(UUID.randomUUID());
        orden.setEventoId(UUID.randomUUID());
        orden.setEventoNombre("Recital");
        orden.setEventoFecha(OffsetDateTime.parse("2027-01-15T21:00:00-03:00"));

        UUID sectorId = UUID.randomUUID();
        UUID asientoId = UUID.randomUUID();
        OrdenItem asiento = new OrdenItem(orden.getId(), sectorId, asientoId, 1,
                new BigDecimal("15000.00"), new BigDecimal("15000.00"));
        asiento.setId(UUID.randomUUID());
        OrdenItem zona = new OrdenItem(orden.getId(), sectorId, null, 2,
                new BigDecimal("8000.00"), new BigDecimal("16000.00"));
        zona.setId(UUID.randomUUID());
        when(entradaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Entrada> entradas = ticketService.emitir(orden, List.of(asiento, zona));

        assertThat(entradas).hasSize(3);
        assertThat(entradas).extracting(Entrada::getQrCode).doesNotHaveDuplicates();
        assertThat(entradas.stream().filter(entrada -> entrada.getAsientoId() != null)).hasSize(1);
        assertThat(entradas.getFirst().getAsientoId()).isEqualTo(asientoId);
        assertThat(entradas.stream().filter(entrada -> entrada.getAsientoId() == null)).hasSize(2);
        assertThat(entradas).allSatisfy(entrada -> {
            assertThat(entrada.getEstado().name()).isEqualTo("VALIDA");
            assertThat(entrada.getClienteId()).isEqualTo(orden.getClienteId());
        });
    }
}
