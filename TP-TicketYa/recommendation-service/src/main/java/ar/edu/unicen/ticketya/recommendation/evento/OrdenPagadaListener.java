package ar.edu.unicen.ticketya.recommendation.evento;

import ar.edu.unicen.ticketya.recommendation.servicio.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume orden.pagada y delega en HistorialService. Un mensaje inválido o una
 * excepción no se reencola infinitamente: la configuración
 * default-requeue-rejected=false lo envía a recommendation.history.dlq.
 */
@Component
public class OrdenPagadaListener {

    private static final Logger log = LoggerFactory.getLogger(OrdenPagadaListener.class);

    private final HistorialService historialService;

    public OrdenPagadaListener(HistorialService historialService) {
        this.historialService = historialService;
    }

    @RabbitListener(queues = "${recommendation.rabbit.queue:recommendation.history}")
    public void escuchar(OrdenPagadaEvent event) {
        log.info("orden.pagada recibida: eventId={} correlationId={}",
                event == null ? null : event.eventId(),
                event == null ? null : event.correlationId());
        historialService.registrar(event);
    }
}
