package ar.edu.unicen.ticketya.notification.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(name = "destinatario_id", nullable = false)
    private UUID destinatarioId;

    @Column(name = "destinatario_email", length = 255)
    private String destinatarioEmail;

    @Column(nullable = false, length = 255)
    private String asunto;

    @Column(nullable = false, columnDefinition = "text")
    private String contenido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    @Column(nullable = false)
    private int intentos = 0;

    @Column(name = "proximo_reintento")
    private OffsetDateTime proximoReintento;

    @Column(name = "ultimo_error", columnDefinition = "text")
    private String ultimoError;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "enviada_at")
    private OffsetDateTime enviadaAt;

    @PrePersist
    void prePersist() {
        if (estado == null) {
            estado = EstadoNotificacion.PENDIENTE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public UUID getDestinatarioId() { return destinatarioId; }
    public void setDestinatarioId(UUID destinatarioId) { this.destinatarioId = destinatarioId; }
    public String getDestinatarioEmail() { return destinatarioEmail; }
    public void setDestinatarioEmail(String destinatarioEmail) { this.destinatarioEmail = destinatarioEmail; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public EstadoNotificacion getEstado() { return estado; }
    public void setEstado(EstadoNotificacion estado) { this.estado = estado; }
    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }
    public OffsetDateTime getProximoReintento() { return proximoReintento; }
    public void setProximoReintento(OffsetDateTime proximoReintento) { this.proximoReintento = proximoReintento; }
    public String getUltimoError() { return ultimoError; }
    public void setUltimoError(String ultimoError) { this.ultimoError = ultimoError; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getEnviadaAt() { return enviadaAt; }
    public void setEnviadaAt(OffsetDateTime enviadaAt) { this.enviadaAt = enviadaAt; }
}
