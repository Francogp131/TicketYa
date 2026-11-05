package ar.edu.unicen.ticketya.catalog.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler de estados del evento (tarea 2.3). Es idempotente: puede correr en
 * varias instancias sin efectos duplicados.
 */
@Component
public class EventoScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventoScheduler.class);

    private final EventService eventService;

    public EventoScheduler(EventService eventService) {
        this.eventService = eventService;
    }

    @Scheduled(fixedDelayString = "${catalog.scheduler.finalizacion-ms:60000}")
    public void finalizarEventosVencidos() {
        int finalizados = eventService.finalizarEventosVencidos();
        if (finalizados > 0) {
            log.info("Eventos finalizados automáticamente: {}", finalizados);
        }
    }

    @Scheduled(fixedDelayString = "${catalog.scheduler.cancelaciones-ms:60000}")
    public void reintentarCancelacionesPendientes() {
        int resueltas = eventService.reintentarCancelacionesPendientes();
        if (resueltas > 0) {
            log.info("Cancelaciones avisadas a orders: {}", resueltas);
        }
    }
}
