package ar.edu.unicen.ticketya.catalog.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false, length = 40)
    private String accion;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "usuario_rol", nullable = false, length = 20)
    private String usuarioRol;

    @Column(nullable = false, length = 20)
    private String resultado;

    @Column(columnDefinition = "text")
    private String detalle;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {}

    public AuditLog(UUID id, UUID eventoId, String accion, UUID usuarioId, String usuarioRol,
                    String resultado, String detalle, String correlationId) {
        this.id = id;
        this.eventoId = eventoId;
        this.accion = accion;
        this.usuarioId = usuarioId;
        this.usuarioRol = usuarioRol;
        this.resultado = resultado;
        this.detalle = detalle;
        this.correlationId = correlationId;
    }

    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public String getAccion() { return accion; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getUsuarioRol() { return usuarioRol; }
    public String getResultado() { return resultado; }
    public String getDetalle() { return detalle; }
    public String getCorrelationId() { return correlationId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
