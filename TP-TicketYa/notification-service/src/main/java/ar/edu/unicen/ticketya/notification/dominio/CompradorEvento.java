package ar.edu.unicen.ticketya.notification.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Proyección local de compradores por evento, alimentada por orden.pagada.
 * Permite notificar cancelaciones y cambios sin consultar orders en el camino
 * crítico y conserva el snapshot previo del evento para el aviso de cambio.
 */
@Entity
@Table(name = "comprador_evento")
public class CompradorEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "cliente_email", length = 255)
    private String clienteEmail;

    @Column(name = "evento_nombre", length = 255)
    private String eventoNombre;

    @Column(name = "evento_fecha")
    private Instant eventoFecha;

    @Column(name = "evento_ciudad", length = 120)
    private String eventoCiudad;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventoId() { return eventoId; }
    public void setEventoId(UUID eventoId) { this.eventoId = eventoId; }
    public UUID getClienteId() { return clienteId; }
    public void setClienteId(UUID clienteId) { this.clienteId = clienteId; }
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }
    public String getEventoNombre() { return eventoNombre; }
    public void setEventoNombre(String eventoNombre) { this.eventoNombre = eventoNombre; }
    public Instant getEventoFecha() { return eventoFecha; }
    public void setEventoFecha(Instant eventoFecha) { this.eventoFecha = eventoFecha; }
    public String getEventoCiudad() { return eventoCiudad; }
    public void setEventoCiudad(String eventoCiudad) { this.eventoCiudad = eventoCiudad; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
