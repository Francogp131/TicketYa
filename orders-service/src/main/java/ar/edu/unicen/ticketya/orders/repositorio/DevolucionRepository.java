package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.Devolucion;
import ar.edu.unicen.ticketya.orders.dominio.EstadoDevolucion;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevolucionRepository extends JpaRepository<Devolucion, UUID> {

    boolean existsByEntradaIdAndEstadoIn(UUID entradaId, Collection<EstadoDevolucion> estados);

    List<Devolucion> findByEstado(EstadoDevolucion estado);
}
