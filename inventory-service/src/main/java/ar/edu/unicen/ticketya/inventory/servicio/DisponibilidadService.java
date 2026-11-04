package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.dominio.AsientoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoAsiento;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.SectorInventario;
import ar.edu.unicen.ticketya.inventory.dominio.TipoSectorInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.AsientoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.SectorInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.DisponibilidadResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisponibilidadService {

    private final EventoInventarioRepository eventoRepository;
    private final SectorInventarioRepository sectorRepository;
    private final AsientoInventarioRepository asientoRepository;

    public DisponibilidadService(EventoInventarioRepository eventoRepository,
                                 SectorInventarioRepository sectorRepository,
                                 AsientoInventarioRepository asientoRepository) {
        this.eventoRepository = eventoRepository;
        this.sectorRepository = sectorRepository;
        this.asientoRepository = asientoRepository;
    }

    @Transactional(readOnly = true)
    public DisponibilidadResponse obtener(UUID eventoId) {
        EventoInventario evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "No existe el evento " + eventoId));
        List<DisponibilidadResponse.Sector> sectores = sectorRepository
                .findByEventoIdOrderByNombreAsc(eventoId).stream()
                .map(sector -> new DisponibilidadResponse.Sector(
                        sector.getSectorId(),
                        sector.getNombre(),
                        sector.getTipo().name(),
                        sector.getPrecio(),
                        sector.getCapacidad(),
                        disponibles(eventoId, sector)))
                .toList();
        return new DisponibilidadResponse(evento.getEventoId(), evento.getEstado().name(), sectores);
    }

    private int disponibles(UUID eventoId, SectorInventario sector) {
        if (sector.getTipo() == TipoSectorInventario.NO_NUMERADO) {
            return sector.getDisponibles() == null ? 0 : sector.getDisponibles();
        }
        return Math.toIntExact(asientoRepository.countByEventoIdAndSectorIdAndEstado(
                eventoId, sector.getSectorId(), EstadoAsiento.LIBRE));
    }

    @Transactional(readOnly = true)
    public List<AsientoInventario> asientos(UUID eventoId, UUID sectorId) {
        eventoRepository.findById(eventoId)
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "No existe el evento " + eventoId));
        SectorInventario sector = sectorRepository.findByEventoIdAndSectorId(eventoId, sectorId)
                .orElseThrow(() -> ApiException.notFound("SECTOR_NOT_FOUND", "No existe el sector " + sectorId));
        if (sector.getTipo() == TipoSectorInventario.NO_NUMERADO) {
            return List.of();
        }
        return asientoRepository.findByEventoIdAndSectorIdOrderByFilaAscNumeroAsc(eventoId, sectorId);
    }
}
