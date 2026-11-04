package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.SectorInventario;
import ar.edu.unicen.ticketya.inventory.dominio.TipoSectorInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.SectorInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventarioService {

    private static final String INSERT_ASIENTO = """
            INSERT INTO asiento_inventario (evento_id, sector_id, asiento_id, fila, numero)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (evento_id, asiento_id) DO NOTHING
            """;

    private final EventoInventarioRepository eventoRepository;
    private final SectorInventarioRepository sectorRepository;
    private final JdbcTemplate jdbcTemplate;

    public InventarioService(EventoInventarioRepository eventoRepository,
                             SectorInventarioRepository sectorRepository,
                             JdbcTemplate jdbcTemplate) {
        this.eventoRepository = eventoRepository;
        this.sectorRepository = sectorRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public InventoryUpsertResponse upsert(UUID eventoId, InventoryUpsertRequest request) {
        if (!eventoId.equals(request.eventoId())) {
            throw ApiException.badRequest("VALIDATION_ERROR", "El eventoId del cuerpo no coincide con la ruta");
        }

        EventoInventario evento = eventoRepository.findById(eventoId).orElse(null);
        if (evento != null && evento.getVersion() >= request.version()) {
            return new InventoryUpsertResponse(eventoId, request.sectores().size(),
                    contarAsientos(request), true);
        }
        if (evento == null) {
            evento = new EventoInventario();
            evento.setEventoId(eventoId);
        }
        evento.setNombre(request.nombre());
        evento.setFecha(request.fecha());
        evento.setLimitePorCliente(request.limitePorCliente());
        evento.setVersion(request.version());
        evento.setUpdatedAt(OffsetDateTime.now());
        eventoRepository.saveAndFlush(evento);

        List<Object[]> asientos = new ArrayList<>();
        for (InventoryUpsertRequest.SectorRequest sectorRequest : request.sectores()) {
            TipoSectorInventario tipo = parseTipo(sectorRequest.tipo());
            SectorInventario sector = sectorRepository
                    .findByEventoIdAndSectorId(eventoId, sectorRequest.sectorId())
                    .orElseGet(SectorInventario::new);
            boolean nuevo = sector.getId() == null;
            sector.setEventoId(eventoId);
            sector.setSectorId(sectorRequest.sectorId());
            sector.setNombre(sectorRequest.nombre());
            sector.setTipo(tipo);
            sector.setPrecio(sectorRequest.precio());
            sector.setCapacidad(sectorRequest.capacidad());
            if (nuevo) {
                sector.setDisponibles(tipo == TipoSectorInventario.NO_NUMERADO
                        ? sectorRequest.capacidad()
                        : null);
            }
            sectorRepository.save(sector);

            if (tipo == TipoSectorInventario.NUMERADO && sectorRequest.asientos() != null) {
                for (InventoryUpsertRequest.AsientoRequest asiento : sectorRequest.asientos()) {
                    asientos.add(new Object[]{
                            eventoId,
                            sectorRequest.sectorId(),
                            asiento.asientoId(),
                            asiento.fila(),
                            asiento.numero()});
                }
            }
        }
        if (!asientos.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_ASIENTO, asientos);
        }
        return new InventoryUpsertResponse(eventoId, request.sectores().size(), asientos.size(), false);
    }

    private int contarAsientos(InventoryUpsertRequest request) {
        return request.sectores().stream()
                .mapToInt(sector -> sector.asientos() == null ? 0 : sector.asientos().size())
                .sum();
    }

    private TipoSectorInventario parseTipo(String tipo) {
        try {
            return TipoSectorInventario.valueOf(tipo);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La solicitud tiene campos inválidos",
                    List.of(Map.of("field", "sectores.tipo", "message", "tipo de sector inválido")));
        }
    }
}
