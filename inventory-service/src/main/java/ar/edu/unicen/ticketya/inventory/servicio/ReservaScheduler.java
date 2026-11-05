package ar.edu.unicen.ticketya.inventory.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Libera periódicamente las reservas vencidas (tarea 3.5). La validación
 * perezosa de la consulta y el pago cubren el caso de que el job todavía no
 * haya corrido.
 */
@Component
public class ReservaScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservaScheduler.class);

    private final ReservaService reservaService;

    public ReservaScheduler(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @Scheduled(fixedDelayString = "${inventory.scheduler.vencimiento-ms:30000}")
    public void liberarVencidas() {
        int liberadas = reservaService.liberarVencidas();
        if (liberadas > 0) {
            log.info("Reservas vencidas liberadas: {}", liberadas);
        }
    }
}
