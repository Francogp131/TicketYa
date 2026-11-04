package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenItemRepository extends JpaRepository<OrdenItem, UUID> {

    List<OrdenItem> findByOrdenId(UUID ordenId);
}
