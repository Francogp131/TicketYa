package ar.edu.unicen.ticketya.catalog.repositorio;

import ar.edu.unicen.ticketya.catalog.dominio.EventoParticipante;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoParticipanteRepository extends JpaRepository<EventoParticipante, UUID> {

    List<EventoParticipante> findByEventoIdOrderByNombreAsc(UUID eventoId);

    void deleteByEventoId(UUID eventoId);
}
