package ar.edu.unicen.ticketya.notification.evento;

import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.servicio.CompradorEventoService;
import ar.edu.unicen.ticketya.notification.servicio.DestinatarioEmail;
import ar.edu.unicen.ticketya.notification.servicio.NotificationService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume orden.pagada y genera la notificación de compra de forma idempotente.
 * NotificationService deduplica por eventId+tipo+destinatario. Un mensaje
 * inválido se rechaza sin reencolar; los errores transitorios se reintentan y,
 * agotados los intentos, terminan en notification.order.dlq.
 */
@Component
public class OrdenPagadaListener {

    private static final Logger log = LoggerFactory.getLogger(OrdenPagadaListener.class);

    private final NotificationService notificationService;
    private final CompradorEventoService compradorEventoService;
    private final DestinatarioEmail destinatarioEmail;

    public OrdenPagadaListener(NotificationService notificationService,
                               CompradorEventoService compradorEventoService,
                               DestinatarioEmail destinatarioEmail) {
        this.notificationService = notificationService;
        this.compradorEventoService = compradorEventoService;
        this.destinatarioEmail = destinatarioEmail;
    }

    @RabbitListener(queues = "${notification.rabbit.queue:notification.order}")
    public void escuchar(OrdenPagadaEvent evento) {
        if (evento == null || evento.eventId() == null || evento.eventId().isBlank()) {
            throw rechazo("orden.pagada sin eventId");
        }
        log.info("orden.pagada recibida: eventId={} correlationId={}",
                evento.eventId(), evento.correlationId());
        if (!OrdenPagadaEvent.TIPO.equals(evento.type())) {
            throw rechazo("tipo de evento inesperado en notification.order: " + evento.type());
        }
        OrdenPagadaPayload payload = evento.payload();
        if (payload == null || payload.ordenId() == null || payload.clienteId() == null
                || payload.eventoId() == null) {
            throw rechazo("orden.pagada sin ordenId, clienteId o eventoId");
        }
        if (!OrdenPagadaPayload.ESTADO_PAGADA.equals(payload.estado())) {
            throw rechazo("orden.pagada con estado inválido: " + payload.estado());
        }

        UUID eventId = uuid(evento.eventId(), "eventId");
        UUID clienteId = uuid(payload.clienteId(), "clienteId");
        uuid(payload.eventoId(), "eventoId");
        compradorEventoService.registrar(payload);

        Notificacion notificacion = new Notificacion();
        notificacion.setEventId(eventId);
        notificacion.setTipo(OrdenPagadaEvent.TIPO);
        notificacion.setDestinatarioId(clienteId);
        notificacion.setDestinatarioEmail(destinatarioEmail.resolver(payload.clienteEmail(), clienteId));
        notificacion.setAsunto(asunto(payload));
        notificacion.setContenido(contenido(payload));
        notificacion.setCorrelationId(evento.correlationId());

        Notificacion resultado = notificationService.crearYEnviar(notificacion);
        log.info("Notificación de compra evento={} cliente={} estado={} intentos={}",
                eventId, clienteId, resultado.getEstado(), resultado.getIntentos());
    }

    private String asunto(OrdenPagadaPayload payload) {
        String nombre = payload.eventoNombre() == null ? "tu evento" : payload.eventoNombre();
        return "Compra confirmada - " + nombre;
    }

    private String contenido(OrdenPagadaPayload payload) {
        String nombre = payload.eventoNombre() == null ? "tu evento" : payload.eventoNombre();
        return "Tu compra de la orden " + payload.ordenId() + " para " + nombre + " fue confirmada.";
    }

    private UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException ex) {
            throw rechazo("orden.pagada con " + campo + " inválido: " + valor);
        }
    }

    private AmqpRejectAndDontRequeueException rechazo(String motivo) {
        log.warn("Rechazando mensaje de notification.order: {}", motivo);
        return new AmqpRejectAndDontRequeueException(motivo);
    }
}
