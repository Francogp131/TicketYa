package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.dominio.Asiento;
import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.Evento;
import ar.edu.unicen.ticketya.catalog.dominio.EventoParticipante;
import ar.edu.unicen.ticketya.catalog.dominio.EventoSectorPrecio;
import ar.edu.unicen.ticketya.catalog.dominio.Lugar;
import ar.edu.unicen.ticketya.catalog.dominio.Sector;
import ar.edu.unicen.ticketya.catalog.repositorio.AsientoRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.EventoParticipanteRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.EventoRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.EventoSectorPrecioRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.LugarRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.SectorRepository;
import ar.edu.unicen.ticketya.catalog.web.ApiException;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearEventoRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoDetalleResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PrecioRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.PrecioResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.SectorDetalleResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventoRepository eventoRepository;
    private final LugarRepository lugarRepository;
    private final SectorRepository sectorRepository;
    private final AsientoRepository asientoRepository;
    private final EventoParticipanteRepository participanteRepository;
    private final EventoSectorPrecioRepository precioRepository;
    private final InventoryClient inventoryClient;
    private final Clock clock;

    public EventService(EventoRepository eventoRepository,
                        LugarRepository lugarRepository,
                        SectorRepository sectorRepository,
                        AsientoRepository asientoRepository,
                        EventoParticipanteRepository participanteRepository,
                        EventoSectorPrecioRepository precioRepository,
                        InventoryClient inventoryClient,
                        Clock clock) {
        this.eventoRepository = eventoRepository;
        this.lugarRepository = lugarRepository;
        this.sectorRepository = sectorRepository;
        this.asientoRepository = asientoRepository;
        this.participanteRepository = participanteRepository;
        this.precioRepository = precioRepository;
        this.inventoryClient = inventoryClient;
        this.clock = clock;
    }

    @Transactional
    public EventoResponse crearEvento(CrearEventoRequest request, UUID organizadorId) {
        if (!request.fecha().isAfter(OffsetDateTime.now(clock))) {
            throw validacion("fecha", "debe ser una fecha futura");
        }
        if (request.limitePorCliente() != null && request.limitePorCliente() < 1) {
            throw validacion("limitePorCliente", "debe ser mayor a 0");
        }
        Lugar lugar = lugarRepository.findById(request.lugarId())
                .orElseThrow(() -> ApiException.notFound("VENUE_NOT_FOUND", "No existe el lugar " + request.lugarId()));

        Evento evento = new Evento();
        evento.setNombre(request.nombre().trim());
        evento.setDescripcion(request.descripcion());
        evento.setTipo(request.tipo());
        evento.setOrganizadorId(organizadorId);
        evento.setLugarId(lugar.getId());
        evento.setFecha(request.fecha());
        evento.setEstado(EstadoEvento.BORRADOR);
        if (request.limitePorCliente() != null) {
            evento.setLimitePorCliente(request.limitePorCliente());
        }
        evento = eventoRepository.save(evento);

        UUID eventoId = evento.getId();
        List<EventoParticipante> participantes = request.participantes().stream()
                .map(nombre -> new EventoParticipante(eventoId, nombre.trim()))
                .toList();
        participanteRepository.saveAll(participantes);

        return EventoResponse.from(evento, lugar.getNombre(), lugar.getCiudad(),
                participantes.stream().map(EventoParticipante::getNombre).toList(), List.of());
    }

    @Transactional
    public PrecioResponse asignarPrecio(UUID eventoId, UUID sectorId, PrecioRequest request, UUID organizadorId) {
        Evento evento = buscarEvento(eventoId);
        requireOwner(evento, organizadorId);
        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw ApiException.conflict("EVENT_NOT_EDITABLE", "El evento ya no está en borrador");
        }
        if (request.precio().signum() < 0) {
            throw ApiException.badRequest("INVALID_PRICE", "El precio no puede ser negativo");
        }
        Sector sector = sectorRepository.findById(sectorId)
                .orElseThrow(() -> ApiException.notFound("SECTOR_NOT_FOUND", "No existe el sector " + sectorId));
        if (!sector.getLugarId().equals(evento.getLugarId())) {
            throw ApiException.badRequest("SECTOR_NOT_FROM_VENUE", "El sector no pertenece al lugar del evento");
        }

        EventoSectorPrecio precio = precioRepository.findByEventoIdAndSectorId(eventoId, sectorId)
                .orElseGet(() -> new EventoSectorPrecio(eventoId, sectorId, request.precio()));
        precio.setPrecio(request.precio());
        precio = precioRepository.save(precio);
        return new PrecioResponse(precio.getEventoId(), precio.getSectorId(), precio.getPrecio());
    }

    @Transactional
    public EventoResponse publicar(UUID eventoId, UUID organizadorId) {
        Evento evento = buscarEvento(eventoId);
        requireOwner(evento, organizadorId);
        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw ApiException.conflict("EVENT_NOT_EDITABLE", "Solo se puede publicar un evento en borrador");
        }

        List<Sector> sectores = sectorRepository.findByLugarIdOrderByNombreAsc(evento.getLugarId());
        Map<UUID, EventoSectorPrecio> precios = precioRepository.findByEventoId(eventoId).stream()
                .collect(Collectors.toMap(EventoSectorPrecio::getSectorId, Function.identity()));
        List<Map<String, Object>> faltantes = sectores.stream()
                .filter(sector -> !precios.containsKey(sector.getId()))
                .map(sector -> Map.<String, Object>of("field", "sectorId", "message", "falta el precio de " + sector.getNombre()))
                .toList();
        if (sectores.isEmpty() || !faltantes.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_INCOMPLETE",
                    "Todos los sectores del lugar deben tener precio antes de publicar", faltantes);
        }

        InventorySnapshot snapshot = armarSnapshot(evento, sectores, precios);
        try {
            inventoryClient.publicarInventario(snapshot);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                    "No se pudo confirmar el inventario del evento; el evento sigue en borrador", List.of());
        }

        evento.setEstado(EstadoEvento.PUBLICADO);
        evento = eventoRepository.save(evento);
        Lugar lugar = lugarRepository.findById(evento.getLugarId()).orElseThrow();
        return EventoResponse.from(evento, lugar.getNombre(), lugar.getCiudad(),
                participanteRepository.findByEventoIdOrderByNombreAsc(eventoId).stream()
                        .map(EventoParticipante::getNombre).toList(),
                precios.values().stream()
                        .map(precio -> new PrecioResponse(precio.getEventoId(), precio.getSectorId(), precio.getPrecio()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public EventoDetalleResponse detalle(UUID eventoId) {
        Evento evento = buscarEvento(eventoId);
        if (evento.getEstado() != EstadoEvento.PUBLICADO && evento.getEstado() != EstadoEvento.AGOTADO) {
            throw ApiException.notFound("EVENT_NOT_FOUND", "No existe un evento visible con id " + eventoId);
        }
        Lugar lugar = lugarRepository.findById(evento.getLugarId())
                .orElseThrow(() -> ApiException.notFound("VENUE_NOT_FOUND", "No existe el lugar del evento"));

        Map<UUID, Integer> disponibilidad;
        try {
            disponibilidad = inventoryClient.obtenerDisponibilidad(eventoId);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INVENTORY_UNAVAILABLE",
                    "No se pudo obtener la disponibilidad del evento", List.of());
        }

        Map<UUID, Sector> sectores = sectorRepository.findByLugarIdOrderByNombreAsc(evento.getLugarId()).stream()
                .collect(Collectors.toMap(Sector::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, BigDecimal> precios = precioRepository.findByEventoId(eventoId).stream()
                .collect(Collectors.toMap(EventoSectorPrecio::getSectorId, EventoSectorPrecio::getPrecio));

        List<SectorDetalleResponse> sectoresResponse = new ArrayList<>();
        precios.forEach((sectorId, precio) -> {
            Sector sector = sectores.get(sectorId);
            if (sector != null) {
                sectoresResponse.add(new SectorDetalleResponse(
                        sector.getId(),
                        sector.getNombre(),
                        sector.getTipo(),
                        precio,
                        sector.getCapacidad(),
                        disponibilidad.getOrDefault(sectorId, 0)));
            }
        });

        return new EventoDetalleResponse(
                evento.getId(),
                evento.getNombre(),
                evento.getDescripcion(),
                evento.getTipo(),
                evento.getLugarId(),
                lugar.getNombre(),
                lugar.getCiudad(),
                evento.getOrganizadorId(),
                ar.edu.unicen.ticketya.catalog.web.dto.Fechas.argentina(evento.getFecha()),
                evento.getEstado(),
                evento.getLimitePorCliente(),
                participanteRepository.findByEventoIdOrderByNombreAsc(eventoId).stream()
                        .map(EventoParticipante::getNombre).toList(),
                sectoresResponse);
    }

    private InventorySnapshot armarSnapshot(Evento evento, List<Sector> sectores,
                                             Map<UUID, EventoSectorPrecio> precios) {
        List<InventorySnapshot.SectorSnapshot> sectoresSnapshot = new ArrayList<>();
        for (Sector sector : sectores) {
            List<InventorySnapshot.AsientoSnapshot> asientos = asientoRepository
                    .findBySectorIdOrderByFilaAscNumeroAsc(sector.getId()).stream()
                    .map(this::asientoSnapshot)
                    .toList();
            sectoresSnapshot.add(new InventorySnapshot.SectorSnapshot(
                    sector.getId(),
                    sector.getNombre(),
                    sector.getTipo().name(),
                    precios.get(sector.getId()).getPrecio(),
                    sector.getCapacidad(),
                    sector.getFilas(),
                    asientos));
        }
        return new InventorySnapshot(
                evento.getId(),
                evento.getNombre(),
                evento.getFecha(),
                evento.getLimitePorCliente(),
                1L,
                sectoresSnapshot);
    }

    private InventorySnapshot.AsientoSnapshot asientoSnapshot(Asiento asiento) {
        return new InventorySnapshot.AsientoSnapshot(asiento.getId(), asiento.getFila(), asiento.getNumero());
    }

    private Evento buscarEvento(UUID eventoId) {
        return eventoRepository.findById(eventoId)
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "No existe el evento " + eventoId));
    }

    private void requireOwner(Evento evento, UUID organizadorId) {
        if (!evento.getOrganizadorId().equals(organizadorId)) {
            throw ApiException.forbidden("NOT_EVENT_OWNER", "El evento pertenece a otro organizador");
        }
    }

    private ApiException validacion(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud tiene campos inválidos", List.of(Map.of("field", field, "message", message)));
    }
}
