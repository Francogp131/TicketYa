package ar.edu.unicen.ticketya.notification.repositorio;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    Optional<Notificacion> findByEventIdAndTipoAndDestinatarioId(UUID eventId, String tipo, UUID destinatarioId);

    List<Notificacion> findByEstadoAndProximoReintentoBefore(EstadoNotificacion estado, OffsetDateTime limite);
}
