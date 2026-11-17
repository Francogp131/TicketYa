package ar.edu.unicen.ticketya.notification.servicio;

import ar.edu.unicen.ticketya.notification.dominio.CompradorEvento;
import ar.edu.unicen.ticketya.notification.evento.OrdenPagadaPayload;
import ar.edu.unicen.ticketya.notification.repositorio.CompradorEventoRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompradorEventoService {

    private final CompradorEventoRepository repository;

    public CompradorEventoService(CompradorEventoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(OrdenPagadaPayload payload) {
        String email = payload.clienteEmail() == null || payload.clienteEmail().isBlank()
                ? null
                : payload.clienteEmail().trim();
        repository.registrar(UUID.randomUUID(),
                UUID.fromString(payload.eventoId()),
                UUID.fromString(payload.clienteId()),
                email,
                payload.eventoNombre(),
                payload.fechaEvento() == null ? null : Timestamp.from(payload.fechaEvento()),
                payload.ciudad());
    }

    @Transactional(readOnly = true)
    public List<CompradorEvento> compradoresDe(UUID eventoId) {
        return repository.findByEventoIdOrderByClienteIdAsc(eventoId);
    }
}
