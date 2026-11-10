package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReintegroRepository extends JpaRepository<Reintegro, UUID> {

    List<Reintegro> findByPagoId(UUID pagoId);

    boolean existsByPagoIdAndEntradaId(UUID pagoId, UUID entradaId);

    List<Reintegro> findByEstado(EstadoReintegro estado);
}
