package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.ReservaItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaItemRepository extends JpaRepository<ReservaItem, UUID> {

    List<ReservaItem> findByReservaId(UUID reservaId);

    /**
     * Entradas comprometidas en reservas vigentes del cliente para el evento.
     * Se usa para el límite por cliente (tarea 3.4).
     */
    @Query("""
            select coalesce(sum(i.cantidad), 0) from ReservaItem i, Reserva r
            where i.reservaId = r.id
              and r.eventoId = :eventoId
              and r.clienteId = :clienteId
              and r.estado = ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva.VIGENTE
              and r.expiresAt > :momento
            """)
    long sumarCantidadVigente(@Param("eventoId") UUID eventoId,
                              @Param("clienteId") UUID clienteId,
                              @Param("momento") OffsetDateTime momento);
}
