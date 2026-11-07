package ar.edu.unicen.ticketya.catalog.dominio;

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
@Table(name = "evento")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoEvento tipo;

    @Column(name = "organizador_id", nullable = false)
    private UUID organizadorId;

    @Column(name = "lugar_id", nullable = false)
    private UUID lugarId;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEvento estado = EstadoEvento.BORRADOR;

    @Column(name = "limite_por_cliente", nullable = false)
    private Integer limitePorCliente = 4;

    @Column(name = "cancelacion_pendiente", nullable = false)
    private boolean cancelacionPendiente = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (estado == null) {
            estado = EstadoEvento.BORRADOR;
        }
        if (limitePorCliente == null || limitePorCliente <= 0) {
            limitePorCliente = 4;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public void setTipo(TipoEvento tipo) {
        this.tipo = tipo;
    }

    public UUID getOrganizadorId() {
        return organizadorId;
    }

    public void setOrganizadorId(UUID organizadorId) {
        this.organizadorId = organizadorId;
    }

    public UUID getLugarId() {
        return lugarId;
    }

    public void setLugarId(UUID lugarId) {
        this.lugarId = lugarId;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public void setFecha(OffsetDateTime fecha) {
        this.fecha = fecha;
    }

    public EstadoEvento getEstado() {
        return estado;
    }

    public void setEstado(EstadoEvento estado) {
        this.estado = estado;
    }

    public Integer getLimitePorCliente() {
        return limitePorCliente;
    }

    public void setLimitePorCliente(Integer limitePorCliente) {
        this.limitePorCliente = limitePorCliente;
    }

    public boolean isCancelacionPendiente() {
        return cancelacionPendiente;
    }

    public void setCancelacionPendiente(boolean cancelacionPendiente) {
        this.cancelacionPendiente = cancelacionPendiente;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
