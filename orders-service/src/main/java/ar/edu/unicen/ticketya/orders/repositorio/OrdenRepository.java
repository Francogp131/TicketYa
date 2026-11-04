package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.Orden;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenRepository extends JpaRepository<Orden, UUID> {

    Optional<Orden> findByIdAndClienteId(UUID id, UUID clienteId);

    Page<Orden> findByClienteIdOrderByCreatedAtDesc(UUID clienteId, Pageable pageable);
}
