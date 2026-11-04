package ar.edu.unicen.ticketya.inventory.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "evento_inventario")
public class EventoInventario {

    @Id
    @Column(name = "evento_id")
    private UUID eventoId;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEventoInventario estado = EstadoEventoInventario.PUBLICADO;

    @Column(name = "limite_por_cliente", nullable = false)
    private Integer limitePorCliente;

    @Column(nullable = false)
    private Long version = 1L;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UUID getEventoId() {
        return eventoId;
    }

    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public void setFecha(OffsetDateTime fecha) {
        this.fecha = fecha;
    }

    public EstadoEventoInventario getEstado() {
        return estado;
    }

    public void setEstado(EstadoEventoInventario estado) {
        this.estado = estado;
    }

    public Integer getLimitePorCliente() {
        return limitePorCliente;
    }

    public void setLimitePorCliente(Integer limitePorCliente) {
        this.limitePorCliente = limitePorCliente;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
