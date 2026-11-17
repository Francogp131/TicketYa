package ar.edu.unicen.ticketya.notification.servicio;

import ar.edu.unicen.ticketya.notification.dominio.EstadoNotificacion;
import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import ar.edu.unicen.ticketya.notification.repositorio.NotificacionRepository;
import ar.edu.unicen.ticketya.notification.web.ApiException;
import ar.edu.unicen.ticketya.notification.web.dto.NotificacionResponse;
import ar.edu.unicen.ticketya.notification.web.dto.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaNotificacionService {

    private static final int TAMANIO_MAXIMO = 100;

    private final NotificacionRepository repository;

    public ConsultaNotificacionService(NotificacionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificacionResponse> listar(UUID clienteId, EstadoNotificacion estado,
                                                     int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizarTamanio(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notificacion> resultado = estado == null
                ? repository.findByDestinatarioId(clienteId, pageable)
                : repository.findByDestinatarioIdAndEstado(clienteId, estado, pageable);
        return new PageResponse<>(
                resultado.getContent().stream().map(this::toResponse).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    @Transactional(readOnly = true)
    public NotificacionResponse obtener(UUID notificacionId, UUID clienteId) {
        Notificacion notificacion = repository.findById(notificacionId)
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND",
                        "La notificación no existe"));
        if (!notificacion.getDestinatarioId().equals(clienteId)) {
            throw ApiException.forbidden("NOTIFICATION_FORBIDDEN",
                    "La notificación pertenece a otro usuario");
        }
        return toResponse(notificacion);
    }

    private int normalizarTamanio(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, TAMANIO_MAXIMO);
    }

    private NotificacionResponse toResponse(Notificacion notificacion) {
        return new NotificacionResponse(
                notificacion.getId(),
                notificacion.getEventId(),
                notificacion.getTipo(),
                notificacion.getAsunto(),
                notificacion.getContenido(),
                notificacion.getEstado(),
                notificacion.getIntentos(),
                notificacion.getProximoReintento(),
                notificacion.getCreatedAt(),
                notificacion.getEnviadaAt());
    }
}
