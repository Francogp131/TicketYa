package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {

    List<Entrada> findByOrdenId(UUID ordenId);

    List<Entrada> findByEventoIdAndEstado(UUID eventoId, EstadoEntrada estado);

    long countByEventoIdAndEstado(UUID eventoId, EstadoEntrada estado);

    Page<Entrada> findByClienteIdOrderByCreatedAtDesc(UUID clienteId, Pageable pageable);

    Page<Entrada> findByClienteIdAndEventoIdOrderByCreatedAtDesc(
            UUID clienteId, UUID eventoId, Pageable pageable);
}
