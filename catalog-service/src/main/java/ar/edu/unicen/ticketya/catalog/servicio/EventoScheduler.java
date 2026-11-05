package ar.edu.unicen.ticketya.catalog.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventoScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventoScheduler.class);

    private final EventService eventService;

    public EventoScheduler(EventService eventService) {
        this.eventService = eventService;
    }

    @Scheduled(fixedDelayString = "${catalog.scheduler.finalizacion-ms:60000}")
    public void ejecutar() {
        int finalizados = eventService.finalizarEventosVencidos();
        int cancelacionesReintentadas = eventService.reintentarCancelacionesPendientes();
        if (finalizados > 0 || cancelacionesReintentadas > 0) {
            log.info("Scheduler de eventos: {} finalizados, {} cancelaciones notificadas",
                    finalizados, cancelacionesReintentadas);
        }
    }
}
