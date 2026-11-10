package ar.edu.unicen.ticketya.inventory.servicio;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisponibilidadReconciliadorTest {

    @Mock
    private EventoInventarioRepository eventoRepository;
    @Mock
    private CatalogClient catalogClient;

    private DisponibilidadReconciliador reconciliador;

    @BeforeEach
    void setUp() {
        reconciliador = new DisponibilidadReconciliador(eventoRepository, catalogClient);
    }

    @Test
    void reenviaElEstadoLocalDeLosEventosEnVenta() {
        EventoInventario publicado = evento(EstadoEventoInventario.PUBLICADO);
        EventoInventario agotado = evento(EstadoEventoInventario.AGOTADO);
        when(eventoRepository.findByEstadoIn(List.of(
                EstadoEventoInventario.PUBLICADO, EstadoEventoInventario.AGOTADO)))
                .thenReturn(List.of(publicado, agotado));

        reconciliador.reconciliar();

        verify(catalogClient).notificarDisponibilidad(publicado.getEventoId(), false);
        verify(catalogClient).notificarDisponibilidad(agotado.getEventoId(), true);
    }

    private EventoInventario evento(EstadoEventoInventario estado) {
        EventoInventario evento = new EventoInventario();
        evento.setEventoId(UUID.randomUUID());
        evento.setEstado(estado);
        return evento;
    }
}
