package ar.edu.unicen.ticketya.catalog.repositorio;

import ar.edu.unicen.ticketya.catalog.dominio.Sector;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorRepository extends JpaRepository<Sector, UUID> {

    List<Sector> findByLugarIdOrderByNombreAsc(UUID lugarId);

    Optional<Sector> findByIdAndLugarId(UUID id, UUID lugarId);
}
