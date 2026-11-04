package ar.edu.unicen.ticketya.catalog.repositorio;

import ar.edu.unicen.ticketya.catalog.dominio.Asiento;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsientoRepository extends JpaRepository<Asiento, UUID> {

    List<Asiento> findBySectorIdOrderByFilaAscNumeroAsc(UUID sectorId);

    long countBySectorId(UUID sectorId);
}
