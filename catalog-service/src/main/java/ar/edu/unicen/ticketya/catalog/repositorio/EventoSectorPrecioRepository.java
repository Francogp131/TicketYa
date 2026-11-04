package ar.edu.unicen.ticketya.catalog.repositorio;

import ar.edu.unicen.ticketya.catalog.dominio.EventoSectorPrecio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoSectorPrecioRepository extends JpaRepository<EventoSectorPrecio, UUID> {

    List<EventoSectorPrecio> findByEventoId(UUID eventoId);

    Optional<EventoSectorPrecio> findByEventoIdAndSectorId(UUID eventoId, UUID sectorId);
}
