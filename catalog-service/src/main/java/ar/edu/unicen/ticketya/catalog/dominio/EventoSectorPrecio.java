package ar.edu.unicen.ticketya.catalog.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "evento_sector_precio")
public class EventoSectorPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    public EventoSectorPrecio() {
    }

    public EventoSectorPrecio(UUID eventoId, UUID sectorId, BigDecimal precio) {
        this.eventoId = eventoId;
        this.sectorId = sectorId;
        this.precio = precio;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public void setSectorId(UUID sectorId) {
        this.sectorId = sectorId;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }
}
