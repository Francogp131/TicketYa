package ar.edu.unicen.ticketya.inventory.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "reserva_item")
public class ReservaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;

    @Column(name = "asiento_id")
    private UUID asientoId;

    @Column(nullable = false)
    private Integer cantidad;

    public ReservaItem() {
    }

    public ReservaItem(UUID reservaId, UUID sectorId, UUID asientoId, Integer cantidad) {
        this.reservaId = reservaId;
        this.sectorId = sectorId;
        this.asientoId = asientoId;
        this.cantidad = cantidad;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReservaId() {
        return reservaId;
    }

    public void setReservaId(UUID reservaId) {
        this.reservaId = reservaId;
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
}
