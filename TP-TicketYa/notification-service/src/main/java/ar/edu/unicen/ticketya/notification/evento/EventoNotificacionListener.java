package ar.edu.unicen.ticketya.notification.evento;

import ar.edu.unicen.ticketya.notification.dominio.CompradorEvento;
import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.servicio.CompradorEventoService;
import ar.edu.unicen.ticketya.notification.servicio.DestinatarioEmail;
import ar.edu.unicen.ticketya.notification.servicio.NotificationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume evento.actualizado y evento.cancelado desde notification.event y
 * avisa a los compradores registrados en la proyección local. La notificación
 * es idempotente por eventId+tipo+destinatario.
 */
@Component
public class EventoNotificacionListener {

    private static final Logger log = LoggerFactory.getLogger(EventoNotificacionListener.class);

    private final CompradorEventoService compradorEventoService;
    private final NotificationService notificationService;
    private final DestinatarioEmail destinatarioEmail;

    public EventoNotificacionListener(CompradorEventoService compradorEventoService,
                                      NotificationService notificationService,
                                      DestinatarioEmail destinatarioEmail) {
        this.compradorEventoService = compradorEventoService;
        this.notificationService = notificationService;
        this.destinatarioEmail = destinatarioEmail;
    }

    @RabbitListener(queues = "${notification.rabbit.event-queue:notification.event}")
    public void escuchar(EventoEvent evento) {
        validar(evento);
        if (!EventoEvent.CANCELADO.equals(evento.type())
                && !EventoEvent.ACTUALIZADO.equals(evento.type())) {
            throw rechazo("tipo inesperado en notification.event: " + evento.type());
        }

        UUID eventId = uuid(evento.eventId(), "eventId");
        UUID eventoId = uuid(evento.payload().eventoId(), "eventoId");
        List<CompradorEvento> compradores = compradorEventoService.compradoresDe(eventoId);
        log.info("{} evento={} compradores={} correlationId={}",
                evento.type(), eventoId, compradores.size(), evento.correlationId());
        if (compradores.isEmpty()) {
            return;
        }

        for (CompradorEvento comprador : compradores) {
            Notificacion notificacion = new Notificacion();
            notificacion.setEventId(eventId);
            notificacion.setTipo(evento.type());
            notificacion.setDestinatarioId(comprador.getClienteId());
            notificacion.setDestinatarioEmail(destinatarioEmail.resolver(
                    comprador.getClienteEmail(), comprador.getClienteId()));
            notificacion.setAsunto(asunto(evento));
            notificacion.setContenido(contenido(evento, comprador));
            notificacion.setCorrelationId(evento.correlationId());
            notificationService.crearYEnviar(notificacion);
        }
    }

    private void validar(EventoEvent evento) {
        if (evento == null || evento.eventId() == null || evento.eventId().isBlank()) {
            throw rechazo("evento.* sin eventId");
        }
        if (evento.payload() == null || evento.payload().eventoId() == null
                || evento.payload().eventoId().isBlank()) {
            throw rechazo("evento.* sin eventoId");
        }
    }

    private String asunto(EventoEvent evento) {
        String nombre = nombre(evento.payload());
        return (EventoEvent.CANCELADO.equals(evento.type()) ? "Evento cancelado - " : "Evento actualizado - ")
                + nombre;
    }

    private String contenido(EventoEvent evento, CompradorEvento comprador) {
        String nombre = nombre(evento.payload());
        if (EventoEvent.CANCELADO.equals(evento.type())) {
            return "El evento " + nombre + " fue cancelado. Tus entradas serán reintegradas.";
        }
        String anterior = describir(comprador.getEventoNombre(), comprador.getEventoFecha(),
                comprador.getEventoCiudad());
        String actual = describir(evento.payload().nombre(), evento.payload().fechaEvento(),
                evento.payload().ciudad());
        if (anterior.isBlank()) {
            return "El evento " + nombre + " cambió. Ahora: " + actual + ".";
        }
        return "El evento " + nombre + " cambió. Antes: " + anterior + ". Ahora: " + actual + ".";
    }

    private String nombre(EventoPayload payload) {
        return payload.nombre() == null || payload.nombre().isBlank() ? "tu evento" : payload.nombre();
    }

    private String describir(String nombre, Instant fecha, String ciudad) {
        List<String> partes = new ArrayList<>();
        if (nombre != null && !nombre.isBlank()) {
            partes.add(nombre);
        }
        if (fecha != null) {
            partes.add(fecha.toString());
        }
        if (ciudad != null && !ciudad.isBlank()) {
            partes.add(ciudad);
        }
        return String.join(" | ", partes);
    }

    private UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException ex) {
            throw rechazo("evento.* con " + campo + " inválido: " + valor);
        }
    }

    private AmqpRejectAndDontRequeueException rechazo(String motivo) {
        log.warn("Rechazando mensaje de notification.event: {}", motivo);
        return new AmqpRejectAndDontRequeueException(motivo);
    }
}
