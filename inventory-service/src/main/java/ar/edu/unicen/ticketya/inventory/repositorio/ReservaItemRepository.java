package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.ReservaItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaItemRepository extends JpaRepository<ReservaItem, UUID> {

    List<ReservaItem> findByReservaId(UUID reservaId);
}
