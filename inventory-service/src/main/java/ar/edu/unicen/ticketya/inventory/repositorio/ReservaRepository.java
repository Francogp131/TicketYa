package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.Reserva;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {
}
