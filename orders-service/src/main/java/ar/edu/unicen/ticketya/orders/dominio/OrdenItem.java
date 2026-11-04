package ar.edu.unicen.ticketya.orders.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "orden_item")
public class OrdenItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "orden_id", nullable = false)
    private UUID ordenId;

    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;

    @Column(name = "asiento_id")
    private UUID asientoId;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    public OrdenItem() {
    }

    public OrdenItem(UUID ordenId, UUID sectorId, UUID asientoId, Integer cantidad,
                     BigDecimal precioUnitario, BigDecimal subtotal) {
        this.ordenId = ordenId;
        this.sectorId = sectorId;
        this.asientoId = asientoId;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(UUID ordenId) {
        this.ordenId = ordenId;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public void setSectorId(UUID sectorId) {
        this.sectorId = sectorId;
    }

    public UUID getAsientoId() {
        return asientoId;
    }

    public void setAsientoId(UUID asientoId) {
        this.asientoId = asientoId;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
