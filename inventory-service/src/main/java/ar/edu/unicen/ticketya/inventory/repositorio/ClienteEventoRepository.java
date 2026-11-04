package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.ClienteEvento;
import ar.edu.unicen.ticketya.inventory.dominio.ClienteEventoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteEventoRepository extends JpaRepository<ClienteEvento, ClienteEventoId> {
}
