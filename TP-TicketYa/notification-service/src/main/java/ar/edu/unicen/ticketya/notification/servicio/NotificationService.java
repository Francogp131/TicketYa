package ar.edu.unicen.ticketya.notification.servicio;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.repositorio.NotificacionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificacionRepository repository;
    private final NotificationSender sender;
    private final Clock clock;
    private final int maxIntentos;
    private final Duration backoffBase;

    public NotificationService(NotificacionRepository repository,
                               NotificationSender sender,
                               Clock clock,
                               @Value("${notification.sender.max-attempts:3}") int maxIntentos,
                               @Value("${notification.sender.backoff-seconds:30}") long backoffSeconds) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
        this.maxIntentos = maxIntentos;
        this.backoffBase = Duration.ofSeconds(backoffSeconds);
    }

    @Transactional
    public Notificacion crearYEnviar(Notificacion entrada) {
        Optional<Notificacion> existente = repository.findByEventIdAndTipoAndDestinatarioId(
                entrada.getEventId(), entrada.getTipo(), entrada.getDestinatarioId());
        Notificacion notificacion = existente.orElseGet(() -> {
            if (entrada.getEstado() == null) {
                entrada.setEstado(EstadoNotificacion.PENDIENTE);
            }
            return repository.save(entrada);
        });
        if (notificacion.getEstado() == EstadoNotificacion.ENVIADA) {
            return notificacion;
        }
        intentarEnvio(notificacion);
        return repository.save(notificacion);
    }

    void intentarEnvio(Notificacion notificacion) {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        try {
            notificacion.setIntentos(notificacion.getIntentos() + 1);
            sender.send(notificacion);
            notificacion.setEstado(EstadoNotificacion.ENVIADA);
            notificacion.setEnviadaAt(ahora);
            notificacion.setUltimoError(null);
            notificacion.setProximoReintento(null);
        } catch (Exception ex) {
            String mensaje = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            notificacion.setUltimoError(mensaje);
            if (notificacion.getIntentos() >= maxIntentos) {
                notificacion.setEstado(EstadoNotificacion.FALLIDA);
                notificacion.setProximoReintento(null);
                log.warn("Notificación {} agotó intentos: {}", notificacion.getId(), mensaje);
            } else {
                notificacion.setEstado(EstadoNotificacion.PENDIENTE);
                long segundos = backoffBase.toSeconds() * notificacion.getIntentos();
                notificacion.setProximoReintento(ahora.plusSeconds(segundos));
            }
        }
    }
}
