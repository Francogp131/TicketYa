package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {

    List<Entrada> findByOrdenId(UUID ordenId);
}
