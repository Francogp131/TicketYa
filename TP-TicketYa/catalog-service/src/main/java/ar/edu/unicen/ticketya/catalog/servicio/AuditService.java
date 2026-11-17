package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.dominio.AuditLog;
import ar.edu.unicen.ticketya.catalog.repositorio.AuditLogRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_CORRELACION = 120;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void registrar(UUID eventoId, String accion, UUID usuarioId, String usuarioRol,
                          String resultado, String detalle, String correlationId) {
        try {
            repository.save(new AuditLog(UUID.randomUUID(), eventoId, accion, usuarioId, usuarioRol,
                    resultado, detalle, truncar(correlationId)));
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar auditoría evento={} accion={} correlationId={}",
                    eventoId, accion, correlationId, ex);
        }
    }

    private String truncar(String correlationId) {
        if (correlationId == null || correlationId.length() <= MAX_CORRELACION) {
            return correlationId;
        }
        return correlationId.substring(0, MAX_CORRELACION);
    }
}
