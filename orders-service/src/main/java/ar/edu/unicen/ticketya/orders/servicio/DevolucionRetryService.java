package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Devolucion;
import ar.edu.unicen.ticketya.orders.dominio.EstadoDevolucion;
import ar.edu.unicen.ticketya.orders.repositorio.DevolucionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Reintenta las devoluciones que quedaron PROCESANDO porque la pasarela o
 * inventory no respondieron. {@code aprobar} es idempotente: si el reintegro
 * ya está aprobado, saltea la pasarela y vuelve a intentar liberar el lugar.
 */
@Service
public class DevolucionRetryService {

    private static final Logger log = LoggerFactory.getLogger(DevolucionRetryService.class);

    private final DevolucionRepository devolucionRepository;
    private final DevolucionService devolucionService;

    public DevolucionRetryService(DevolucionRepository devolucionRepository,
                                  DevolucionService devolucionService) {
        this.devolucionRepository = devolucionRepository;
        this.devolucionService = devolucionService;
    }

    @Scheduled(fixedDelayString = "${orders.devoluciones.retry-delay-ms:60000}")
    public void reintentarProcesando() {
        List<Devolucion> procesando = devolucionRepository.findByEstado(EstadoDevolucion.PROCESANDO);
        for (Devolucion devolucion : procesando) {
            try {
                devolucionService.aprobar(devolucion.getId());
            } catch (RuntimeException ex) {
                log.warn("Sigue pendiente la devolución {}: {}", devolucion.getId(), ex.getMessage());
            }
        }
    }
}
