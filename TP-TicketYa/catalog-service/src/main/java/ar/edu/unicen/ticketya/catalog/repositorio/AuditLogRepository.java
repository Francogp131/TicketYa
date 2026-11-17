package ar.edu.unicen.ticketya.catalog.repositorio;

import ar.edu.unicen.ticketya.catalog.dominio.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEventoIdOrderByCreatedAtDesc(UUID eventoId);

    List<AuditLog> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);
}
