package ar.edu.unicen.ticketya.catalog.repositorio;

import ar.edu.unicen.ticketya.catalog.dominio.Evento;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoRepository extends JpaRepository<Evento, UUID> {
}
