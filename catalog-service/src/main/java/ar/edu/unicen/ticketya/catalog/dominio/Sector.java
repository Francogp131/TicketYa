package ar.edu.unicen.ticketya.catalog.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sector")
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lugar_id", nullable = false)
    private UUID lugarId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSector tipo;

    @Column(nullable = false)
    private Integer capacidad;

    private Integer filas;

    @Column(name = "asientos_por_fila")
    private Integer asientosPorFila;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLugarId() {
        return lugarId;
    }

    public void setLugarId(UUID lugarId) {
        this.lugarId = lugarId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public TipoSector getTipo() {
        return tipo;
    }

    public void setTipo(TipoSector tipo) {
        this.tipo = tipo;
    }

    public Integer getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(Integer capacidad) {
        this.capacidad = capacidad;
    }

    public Integer getFilas() {
        return filas;
    }

    public void setFilas(Integer filas) {
        this.filas = filas;
    }

    public Integer getAsientosPorFila() {
        return asientosPorFila;
    }

    public void setAsientosPorFila(Integer asientosPorFila) {
        this.asientosPorFila = asientosPorFila;
    }
}
