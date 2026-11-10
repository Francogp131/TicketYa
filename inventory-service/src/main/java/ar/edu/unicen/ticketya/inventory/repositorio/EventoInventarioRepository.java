package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoInventarioRepository extends JpaRepository<EventoInventario, UUID> {

    List<EventoInventario> findByEstadoIn(Collection<EstadoEventoInventario> estados);
}
