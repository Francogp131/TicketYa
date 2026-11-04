package ar.edu.unicen.ticketya.inventory.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ClienteEventoId implements Serializable {

    @Column(name = "evento_id")
    private UUID eventoId;

    @Column(name = "cliente_id")
    private UUID clienteId;

    public ClienteEventoId() {
    }

    public ClienteEventoId(UUID eventoId, UUID clienteId) {
        this.eventoId = eventoId;
        this.clienteId = clienteId;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public void setClienteId(UUID clienteId) {
        this.clienteId = clienteId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClienteEventoId that)) {
            return false;
        }
        return Objects.equals(eventoId, that.eventoId) && Objects.equals(clienteId, that.clienteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventoId, clienteId);
    }
}
