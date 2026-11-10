package ar.edu.unicen.ticketya.orders.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reintegro")
public class Reintegro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pago_id", nullable = false)
    private UUID pagoId;

    @Column(name = "entrada_id")
    private UUID entradaId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReintegro estado;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public Reintegro() {
    }

    public Reintegro(UUID pagoId, UUID entradaId, BigDecimal monto, EstadoReintegro estado) {
        this.pagoId = pagoId;
        this.entradaId = entradaId;
        this.monto = monto;
        this.estado = estado;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPagoId() {
        return pagoId;
    }

    public void setPagoId(UUID pagoId) {
        this.pagoId = pagoId;
    }

    public UUID getEntradaId() {
        return entradaId;
    }

    public void setEntradaId(UUID entradaId) {
        this.entradaId = entradaId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoReintegro getEstado() {
        return estado;
    }

    public void setEstado(EstadoReintegro estado) {
        this.estado = estado;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
