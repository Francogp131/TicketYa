package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.SectorInventario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorInventarioRepository extends JpaRepository<SectorInventario, UUID> {

    List<SectorInventario> findByEventoIdOrderByNombreAsc(UUID eventoId);

    Optional<SectorInventario> findByEventoIdAndSectorId(UUID eventoId, UUID sectorId);
}
