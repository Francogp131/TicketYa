package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoInventarioRepository extends JpaRepository<EventoInventario, UUID> {
}
