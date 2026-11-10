package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadoEventoService {

    private final EventoInventarioRepository eventoRepository;
    private final Clock clock;

    public EstadoEventoService(EventoInventarioRepository eventoRepository, Clock clock) {
        this.eventoRepository = eventoRepository;
        this.clock = clock;
    }

    @Transactional
    public void actualizar(UUID eventoId, String estado) {
        EstadoEventoInventario destino = interpretar(estado);
        EventoInventario evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "No existe el evento " + eventoId));
        if (evento.getEstado() == destino) {
            return;
        }
        if (evento.getEstado() == EstadoEventoInventario.CANCELADO
                || evento.getEstado() == EstadoEventoInventario.FINALIZADO) {
            throw ApiException.conflict("EVENT_NOT_EDITABLE",
                    "Un evento " + evento.getEstado().name().toLowerCase() + " no cambia de estado");
        }
        evento.setEstado(destino);
        evento.setUpdatedAt(OffsetDateTime.now(clock));
        eventoRepository.save(evento);
    }

    private static EstadoEventoInventario interpretar(String estado) {
        if (estado == null || estado.isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "El estado es obligatorio");
        }
        EstadoEventoInventario destino;
        try {
            destino = EstadoEventoInventario.valueOf(estado.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Estado de evento inválido");
        }
        if (destino != EstadoEventoInventario.CANCELADO && destino != EstadoEventoInventario.FINALIZADO) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "Solo se aceptan los estados CANCELADO y FINALIZADO");
        }
        return destino;
    }
}
