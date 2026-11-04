package ar.edu.unicen.ticketya.catalog.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "asiento")
public class Asiento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;

    @Column(nullable = false)
    private Integer fila;

    @Column(nullable = false)
    private Integer numero;

    public Asiento() {
    }

    public Asiento(UUID sectorId, Integer fila, Integer numero) {
        this.sectorId = sectorId;
        this.fila = fila;
        this.numero = numero;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public void setSectorId(UUID sectorId) {
        this.sectorId = sectorId;
    }

    public Integer getFila() {
        return fila;
    }

    public void setFila(Integer fila) {
        this.fila = fila;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }
}
