package ar.edu.unicen.ticketya.inventory.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cliente_evento")
public class ClienteEvento {

    @EmbeddedId
    private ClienteEventoId id;

    @Column(name = "entradas_vendidas", nullable = false)
    private Integer entradasVendidas = 0;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ClienteEventoId getId() {
        return id;
    }

    public void setId(ClienteEventoId id) {
        this.id = id;
    }

    public Integer getEntradasVendidas() {
        return entradasVendidas;
    }

    public void setEntradasVendidas(Integer entradasVendidas) {
        this.entradasVendidas = entradasVendidas;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
