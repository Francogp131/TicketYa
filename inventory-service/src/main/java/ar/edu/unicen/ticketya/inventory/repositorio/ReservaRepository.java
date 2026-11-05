package ar.edu.unicen.ticketya.inventory.repositorio;

import ar.edu.unicen.ticketya.inventory.dominio.Reserva;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    List<Reserva> findByEstadoAndExpiresAtBefore(EstadoReserva estado, OffsetDateTime momento);
}
