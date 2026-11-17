package ar.edu.unicen.ticketya.notification.evento;

import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.servicio.DestinatarioEmail;
import ar.edu.unicen.ticketya.notification.servicio.NotificationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume evento.publicado desde notification.favorite y avisa a los clientes
 * que tienen alguno de los artistas del evento como favorito en su ciudad.
 * La búsqueda se delega en recommendation-service; si no está disponible se
 * degrada a lista vacía y el evento se publica igual.
 */
@Component
public class EventoPublicadoListener {

    private static final Logger log = LoggerFactory.getLogger(EventoPublicadoListener.class);

    private final PreferenceClient preferenceClient;
    private final NotificationService notificationService;
    private final DestinatarioEmail destinatarioEmail;

    public EventoPublicadoListener(PreferenceClient preferenceClient,
                                   NotificationService notificationService,
                                   DestinatarioEmail destinatarioEmail) {
        this.preferenceClient = preferenceClient;
        this.notificationService = notificationService;
        this.destinatarioEmail = destinatarioEmail;
    }

    @RabbitListener(queues = "${notification.rabbit.favorite-queue:notification.favorite}")
    public void escuchar(EventoEvent evento) {
        if (evento == null || evento.eventId() == null || evento.eventId().isBlank()) {
            throw rechazo("evento.publicado sin eventId");
        }
        if (!EventoEvent.PUBLICADO.equals(evento.type())) {
            throw rechazo("tipo inesperado en notification.favorite: " + evento.type());
        }
        EventoPayload payload = evento.payload();
        if (payload == null || payload.eventoId() == null || payload.eventoId().isBlank()) {
            throw rechazo("evento.publicado sin eventoId");
        }
        String ciudad = payload.ciudad();
        if (payload.artistas() == null || payload.artistas().isEmpty()
                || ciudad == null || ciudad.isBlank()) {
            log.info("evento.publicado evento={} sin artistas o ciudad; no hay avisos de favoritos",
                    payload.eventoId());
            return;
        }

        UUID eventId = uuid(evento.eventId(), "eventId");
        UUID eventoId = uuid(payload.eventoId(), "eventoId");
        Map<UUID, String> destinatarios = new LinkedHashMap<>();
        for (String artista : payload.artistas()) {
            if (artista == null || artista.isBlank()) {
                continue;
            }
            for (PreferenciaCliente preferencia : preferenceClient.buscarPorArtistaYCiudad(artista, ciudad)) {
                UUID clienteId = uuid(preferencia.clienteId(), "clienteId de preferencia");
                if (!destinatarios.containsKey(clienteId) || destinatarios.get(clienteId) == null) {
                    destinatarios.put(clienteId, preferencia.clienteEmail());
                }
            }
        }
        log.info("evento.publicado evento={} artistas={} ciudad={} destinatarios={} correlationId={}",
                eventoId, payload.artistas(), ciudad, destinatarios.size(), evento.correlationId());
        if (destinatarios.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, String> destinatario : destinatarios.entrySet()) {
            Notificacion notificacion = new Notificacion();
            notificacion.setEventId(eventId);
            notificacion.setTipo(EventoEvent.PUBLICADO);
            notificacion.setDestinatarioId(destinatario.getKey());
            notificacion.setDestinatarioEmail(destinatarioEmail.resolver(
                    destinatario.getValue(), destinatario.getKey()));
            notificacion.setAsunto("Nuevo evento - " + nombre(payload));
            notificacion.setContenido(contenido(payload));
            notificacion.setCorrelationId(evento.correlationId());
            notificationService.crearYEnviar(notificacion);
        }
    }

    private String nombre(EventoPayload payload) {
        return payload.nombre() == null || payload.nombre().isBlank() ? "tu evento" : payload.nombre();
    }

    private String contenido(EventoPayload payload) {
        StringBuilder texto = new StringBuilder("El evento ").append(nombre(payload))
                .append(" se publicó en ").append(payload.ciudad());
        if (payload.fechaEvento() != null) {
            texto.append(" el ").append(payload.fechaEvento());
        }
        List<String> artistas = payload.artistas() == null ? List.of() : payload.artistas();
        if (!artistas.isEmpty()) {
            texto.append(". Artistas: ").append(String.join(", ", artistas));
        }
        return texto.append('.').toString();
    }

    private UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw rechazo("evento.publicado con " + campo + " inválido: " + valor);
        }
    }

    private AmqpRejectAndDontRequeueException rechazo(String motivo) {
        log.warn("Rechazando mensaje de notification.favorite: {}", motivo);
        return new AmqpRejectAndDontRequeueException(motivo);
    }
}
