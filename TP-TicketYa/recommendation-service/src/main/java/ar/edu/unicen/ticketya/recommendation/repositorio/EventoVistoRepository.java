package ar.edu.unicen.ticketya.recommendation.repositorio;

import ar.edu.unicen.ticketya.recommendation.dominio.EventoVisto;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventoVistoRepository extends MongoRepository<EventoVisto, String> {

    List<EventoVisto> findByEstadoInAndFechaEventoAfter(Collection<String> estados, Instant fechaDesde);
}
