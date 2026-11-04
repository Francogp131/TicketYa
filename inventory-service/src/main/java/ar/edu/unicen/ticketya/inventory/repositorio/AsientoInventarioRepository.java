package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.AsientoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoAsiento;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsientoInventarioRepository extends JpaRepository<AsientoInventario, UUID> {

    /**
     * Bloquea los asientos pedidos en orden de asientoId para que dos reservas
     * concurrentes no puedan tomar el mismo lugar y para evitar deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from AsientoInventario a
            where a.eventoId = :eventoId and a.asientoId in :asientoIds
            order by a.asientoId asc
            """)
    List<AsientoInventario> bloquearAsientos(@Param("eventoId") UUID eventoId,
                                             @Param("asientoIds") Collection<UUID> asientoIds);

    long countByEventoIdAndSectorIdAndEstado(UUID eventoId, UUID sectorId, EstadoAsiento estado);

    List<AsientoInventario> findByEventoIdAndSectorIdOrderByFilaAscNumeroAsc(UUID eventoId, UUID sectorId);
}
