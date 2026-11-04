package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.Pago;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, UUID> {

    List<Pago> findByOrdenId(UUID ordenId);
}
