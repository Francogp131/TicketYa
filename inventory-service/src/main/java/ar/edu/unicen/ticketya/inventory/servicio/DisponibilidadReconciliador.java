package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Reconciliación periódica del estado AGOTADO/PUBLICADO hacia catalog.
 *
 * <p>El aviso normal es fire-and-forget: si catalog está caído, el estado puede quedar
 * desfasado o dos avisos pueden llegar desordenados. Este job reenvía el estado local
 * actual de todos los eventos en venta, así el próximo tick corrige cualquier drift.</p>
 */
@Service
public class DisponibilidadReconciliador {

    private static final Logger log = LoggerFactory.getLogger(DisponibilidadReconciliador.class);

    private final EventoInventarioRepository eventoRepository;
    private final CatalogClient catalogClient;

    public DisponibilidadReconciliador(EventoInventarioRepository eventoRepository,
                                       CatalogClient catalogClient) {
        this.eventoRepository = eventoRepository;
        this.catalogClient = catalogClient;
    }

    @Scheduled(fixedDelayString = "${inventory.disponibilidad.reconciliacion-ms:300000}")
    public void reconciliar() {
        List<EventoInventario> eventos = eventoRepository.findByEstadoIn(
                List.of(EstadoEventoInventario.PUBLICADO, EstadoEventoInventario.AGOTADO));
        for (EventoInventario evento : eventos) {
            log.debug("Reconciliando disponibilidad de {} ({})", evento.getEventoId(), evento.getEstado());
            catalogClient.notificarDisponibilidad(evento.getEventoId(),
                    evento.getEstado() == EstadoEventoInventario.AGOTADO);
        }
    }
}
