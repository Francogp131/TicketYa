package ar.edu.unicen.ticketya.recommendation.repositorio;

import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HistorialCompraRepository extends MongoRepository<HistorialCompra, String> {

    List<HistorialCompra> findByClienteId(String clienteId);

    List<HistorialCompra> findByClienteIdIn(Collection<String> clienteIds);

    List<HistorialCompra> findByEventoIdIn(Collection<String> eventoIds);

    long countByEventoId(String eventoId);
}
