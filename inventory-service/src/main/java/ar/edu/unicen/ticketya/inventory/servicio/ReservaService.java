package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.dominio.AsientoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.ClienteEvento;
import ar.edu.unicen.ticketya.inventory.dominio.ClienteEventoId;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoAsiento;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.Reserva;
import ar.edu.unicen.ticketya.inventory.dominio.ReservaItem;
import ar.edu.unicen.ticketya.inventory.dominio.SectorInventario;
import ar.edu.unicen.ticketya.inventory.dominio.TipoSectorInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.AsientoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ClienteEventoRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaItemRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.SectorInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReturnRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservaService {

    private static final int MINUTOS_VIGENCIA = 10;

    private final EventoInventarioRepository eventoRepository;
    private final SectorInventarioRepository sectorRepository;
    private final AsientoInventarioRepository asientoRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaItemRepository reservaItemRepository;
    private final ClienteEventoRepository clienteEventoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ReservaService(EventoInventarioRepository eventoRepository,
                          SectorInventarioRepository sectorRepository,
                          AsientoInventarioRepository asientoRepository,
                          ReservaRepository reservaRepository,
                          ReservaItemRepository reservaItemRepository,
                          ClienteEventoRepository clienteEventoRepository,
                          JdbcTemplate jdbcTemplate,
                          Clock clock) {
        this.eventoRepository = eventoRepository;
        this.sectorRepository = sectorRepository;
        this.asientoRepository = asientoRepository;
        this.reservaRepository = reservaRepository;
        this.reservaItemRepository = reservaItemRepository;
        this.clienteEventoRepository = clienteEventoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public ReservationResponse reservar(ReservaRequest request, UUID clienteId) {
        EventoInventario evento = eventoRepository.findById(request.eventoId())
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND",
                        "No existe el evento " + request.eventoId()));
        if (evento.getEstado() != EstadoEventoInventario.PUBLICADO
                && evento.getEstado() != EstadoEventoInventario.AGOTADO) {
            throw ApiException.conflict("EVENT_NOT_ON_SALE", "El evento no está en venta");
        }

        Map<UUID, SectorInventario> sectores = new LinkedHashMap<>();
        List<ReservaRequest.Item> itemsNumerados = new ArrayList<>();
        List<ReservaRequest.Item> itemsZona = new ArrayList<>();
        Set<UUID> asientosPedidos = new HashSet<>();
        int entradasPedidas = 0;

        for (ReservaRequest.Item item : request.items()) {
            SectorInventario sector = sectorRepository
                    .findByEventoIdAndSectorId(evento.getEventoId(), item.sectorId())
                    .orElseThrow(() -> ApiException.notFound("SECTOR_NOT_FOUND",
                            "No existe el sector " + item.sectorId() + " en el evento"));
            sectores.put(sector.getSectorId(), sector);
            boolean esNumerado = sector.getTipo() == TipoSectorInventario.NUMERADO;
            if (esNumerado) {
                if (item.asientoIds() == null || item.asientoIds().isEmpty() || item.cantidad() != null) {
                    throw validacion("items", "un ítem numerado requiere asientoIds y no admite cantidad");
                }
                for (UUID asientoId : item.asientoIds()) {
                    if (!asientosPedidos.add(asientoId)) {
                        throw validacion("items", "hay asientos repetidos en la solicitud");
                    }
                }
                entradasPedidas += item.asientoIds().size();
                itemsNumerados.add(item);
            } else {
                if (item.cantidad() == null || item.cantidad() < 1
                        || (item.asientoIds() != null && !item.asientoIds().isEmpty())) {
                    throw validacion("items", "un ítem de zona requiere cantidad y no admite asientoIds");
                }
                entradasPedidas += item.cantidad();
                itemsZona.add(item);
            }
        }

        validarLimitePorCliente(evento, clienteId, entradasPedidas);

        OffsetDateTime ahora = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = ahora.plusMinutes(MINUTOS_VIGENCIA);
        Reserva reserva = new Reserva();
        reserva.setEventoId(evento.getEventoId());
        reserva.setClienteId(clienteId);
        reserva.setEstado(EstadoReserva.VIGENTE);
        reserva.setExpiresAt(expiresAt);
        reserva = reservaRepository.save(reserva);

        if (!asientosPedidos.isEmpty()) {
            List<AsientoInventario> asientos = asientoRepository
                    .bloquearAsientos(evento.getEventoId(), asientosPedidos);
            if (asientos.size() != asientosPedidos.size()) {
                throw ApiException.conflict("SEAT_NOT_AVAILABLE", "Uno o más asientos ya no están disponibles");
            }
            for (AsientoInventario asiento : asientos) {
                if (asiento.getEstado() != EstadoAsiento.LIBRE) {
                    throw ApiException.conflict("SEAT_NOT_AVAILABLE",
                            "El asiento " + asiento.getAsientoId() + " ya no está disponible");
                }
                asiento.setEstado(EstadoAsiento.RESERVADO);
                asiento.setReservaId(reserva.getId());
                asiento.setExpiresAt(expiresAt);
            }
            asientoRepository.saveAll(asientos);
        }

        List<ReservaItem> reservaItems = new ArrayList<>();
        for (ReservaRequest.Item item : itemsZona) {
            int cantidad = item.cantidad();
            try {
                jdbcTemplate.queryForObject("""
                        UPDATE sector_inventario
                           SET disponibles = disponibles - ?
                         WHERE evento_id = ? AND sector_id = ?
                           AND tipo = 'NO_NUMERADO' AND disponibles >= ?
                        RETURNING disponibles
                        """, Integer.class, cantidad, evento.getEventoId(), item.sectorId(), cantidad);
            } catch (EmptyResultDataAccessException ex) {
                throw ApiException.conflict("INSUFFICIENT_AVAILABILITY",
                        "No hay disponibilidad suficiente en la zona " + item.sectorId());
            }
            reservaItems.add(new ReservaItem(reserva.getId(), item.sectorId(), null, cantidad));
        }
        for (ReservaRequest.Item item : itemsNumerados) {
            for (UUID asientoId : item.asientoIds()) {
                reservaItems.add(new ReservaItem(reserva.getId(), item.sectorId(), asientoId, 1));
            }
        }
        reservaItemRepository.saveAll(reservaItems);

        return toResponse(reserva, evento, sectores, reservaItems);
    }

    /**
     * Validación perezosa: si la reserva venció, se libera en el acto y se
     * devuelve en estado VENCIDA (el 410 se reserva para el pago).
     */
    @Transactional
    public ReservationResponse obtener(UUID reservaId, UUID clienteId) {
        Reserva reserva = buscarReserva(reservaId);
        if (!reserva.getClienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_RESERVATION_OWNER", "La reserva pertenece a otro cliente");
        }
        reserva = vencerSiCorresponde(reserva);
        return toResponse(reserva);
    }

    @Transactional
    public ReservationResponse obtenerInterno(UUID reservaId) {
        return toResponse(vencerSiCorresponde(buscarReserva(reservaId)));
    }

    /**
     * Cancelación explícita del dueño. Idempotente: liberar dos veces devuelve 204.
     */
    @Transactional
    public void cancelar(UUID reservaId, UUID clienteId) {
        Reserva reserva = buscarReserva(reservaId);
        if (!reserva.getClienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_RESERVATION_OWNER", "La reserva pertenece a otro cliente");
        }
        liberarSiVigente(reserva, EstadoReserva.LIBERADA);
    }

    /**
     * Job de vencimiento (tarea 3.5). Devuelve cuántas reservas liberó.
     */
    @Transactional
    public int liberarVencidas() {
        List<Reserva> vencidas = reservaRepository.findByEstadoAndExpiresAtBefore(
                EstadoReserva.VIGENTE, OffsetDateTime.now(clock));
        int liberadas = 0;
        for (Reserva reserva : vencidas) {
            if (liberarSiVigente(reserva, EstadoReserva.VENCIDA)) {
                liberadas++;
            }
        }
        return liberadas;
    }

    /**
     * Confirmación de venta (tarea 3.6). Idempotente sobre reservas CONFIRMADA.
     */
    @Transactional
    public ReservationResponse confirmar(UUID reservaId) {
        Reserva reserva = buscarReserva(reservaId);
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA) {
            return toResponse(reserva);
        }
        if (reserva.getEstado() != EstadoReserva.VIGENTE) {
            throw ApiException.conflict("RESERVATION_NOT_CONFIRMABLE",
                    "La reserva no está vigente y no se puede confirmar");
        }
        if (!reserva.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            liberarSiVigente(reserva, EstadoReserva.VENCIDA);
            throw ApiException.gone("RESERVATION_EXPIRED", "La reserva está vencida");
        }

        List<ReservaItem> items = reservaItemRepository.findByReservaId(reservaId);
        Set<UUID> asientosDeLaReserva = new HashSet<>();
        int entradas = 0;
        for (ReservaItem item : items) {
            entradas += item.getCantidad() == null ? 0 : item.getCantidad();
            if (item.getAsientoId() != null) {
                asientosDeLaReserva.add(item.getAsientoId());
            }
        }
        if (!asientosDeLaReserva.isEmpty()) {
            List<AsientoInventario> asientos = asientoRepository
                    .bloquearAsientos(reserva.getEventoId(), asientosDeLaReserva);
            for (AsientoInventario asiento : asientos) {
                if (!reservaId.equals(asiento.getReservaId())
                        || asiento.getEstado() != EstadoAsiento.RESERVADO) {
                    throw ApiException.conflict("RESERVATION_NOT_CONFIRMABLE",
                            "El asiento " + asiento.getAsientoId() + " ya no pertenece a la reserva");
                }
                asiento.setEstado(EstadoAsiento.VENDIDO);
                asiento.setExpiresAt(null);
            }
            asientoRepository.saveAll(asientos);
        }

        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);
        registrarVenta(reserva.getEventoId(), reserva.getClienteId(), entradas);
        return toResponse(reserva);
    }

    /**
     * Liberación pedida por orders (pago rechazado, error de pasarela, etc.).
     */
    @Transactional
    public void liberar(UUID reservaId, String motivo) {
        Reserva reserva = buscarReserva(reservaId);
        liberarSiVigente(reserva, EstadoReserva.LIBERADA);
    }

    /**
     * Liberación por devolución aprobada (tarea 3.6). Los asientos son
     * idempotentes por estado; las zonas se deduplican por `referencia`.
     */
    @Transactional
    public void devolver(ReturnRequest request) {
        eventoRepository.findById(request.eventoId())
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND",
                        "No existe el evento " + request.eventoId()));

        if (request.referencia() != null) {
            int insertadas = jdbcTemplate.update("""
                    INSERT INTO devolucion_procesada (referencia, evento_id)
                    VALUES (?, ?)
                    ON CONFLICT (referencia) DO NOTHING
                    """, request.referencia(), request.eventoId());
            if (insertadas == 0) {
                return;
            }
        }

        Map<UUID, ReturnRequest.Item> itemsPorAsiento = new HashMap<>();
        for (ReturnRequest.Item item : request.items()) {
            if (item.asientoId() != null) {
                itemsPorAsiento.put(item.asientoId(), item);
            } else {
                if (item.cantidad() == null || item.cantidad() < 1) {
                    throw validacion("items", "un ítem de zona requiere cantidad");
                }
                jdbcTemplate.update("""
                        UPDATE sector_inventario
                           SET disponibles = LEAST(capacidad, COALESCE(disponibles, 0) + ?)
                         WHERE evento_id = ? AND sector_id = ? AND tipo = 'NO_NUMERADO'
                        """, item.cantidad(), request.eventoId(), item.sectorId());
            }
        }

        if (!itemsPorAsiento.isEmpty()) {
            List<AsientoInventario> asientos = asientoRepository
                    .bloquearAsientos(request.eventoId(), itemsPorAsiento.keySet());
            List<AsientoInventario> liberados = new ArrayList<>();
            for (AsientoInventario asiento : asientos) {
                ReturnRequest.Item item = itemsPorAsiento.get(asiento.getAsientoId());
                if (item == null || !item.sectorId().equals(asiento.getSectorId())) {
                    continue;
                }
                if (asiento.getEstado() == EstadoAsiento.VENDIDO) {
                    asiento.setEstado(EstadoAsiento.LIBRE);
                    asiento.setReservaId(null);
                    asiento.setExpiresAt(null);
                    liberados.add(asiento);
                }
            }
            asientoRepository.saveAll(liberados);
        }
    }

    private void validarLimitePorCliente(EventoInventario evento, UUID clienteId, int entradasPedidas) {
        int limite = evento.getLimitePorCliente() == null ? 0 : evento.getLimitePorCliente();
        long vigentes = reservaItemRepository.sumarCantidadVigente(
                evento.getEventoId(), clienteId, OffsetDateTime.now(clock));
        int vendidas = clienteEventoRepository
                .findById(new ClienteEventoId(evento.getEventoId(), clienteId))
                .map(ClienteEvento::getEntradasVendidas)
                .orElse(0);
        if (limite > 0 && vigentes + vendidas + entradasPedidas > limite) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLIENT_LIMIT_EXCEEDED",
                    "El cliente ya alcanzó el límite de " + limite + " entradas para el evento",
                    List.of(Map.of("field", "items",
                            "message", "el límite por cliente es " + limite)));
        }
    }

    private Reserva vencerSiCorresponde(Reserva reserva) {
        if (reserva.getEstado() == EstadoReserva.VIGENTE
                && !reserva.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            liberarSiVigente(reserva, EstadoReserva.VENCIDA);
        }
        return reserva;
    }

    private boolean liberarSiVigente(Reserva reserva, EstadoReserva estadoDestino) {
        if (reserva.getEstado() != EstadoReserva.VIGENTE) {
            return false;
        }
        List<ReservaItem> items = reservaItemRepository.findByReservaId(reserva.getId());
        Set<UUID> asientosIds = new HashSet<>();
        for (ReservaItem item : items) {
            if (item.getAsientoId() != null) {
                asientosIds.add(item.getAsientoId());
            }
        }
        if (!asientosIds.isEmpty()) {
            List<AsientoInventario> asientos = asientoRepository
                    .bloquearAsientos(reserva.getEventoId(), asientosIds);
            List<AsientoInventario> liberados = new ArrayList<>();
            for (AsientoInventario asiento : asientos) {
                if (reserva.getId().equals(asiento.getReservaId())
                        && asiento.getEstado() == EstadoAsiento.RESERVADO) {
                    asiento.setEstado(EstadoAsiento.LIBRE);
                    asiento.setReservaId(null);
                    asiento.setExpiresAt(null);
                    liberados.add(asiento);
                }
            }
            asientoRepository.saveAll(liberados);
        }
        for (ReservaItem item : items) {
            if (item.getAsientoId() == null && item.getCantidad() != null) {
                jdbcTemplate.update("""
                        UPDATE sector_inventario
                           SET disponibles = LEAST(capacidad, COALESCE(disponibles, 0) + ?)
                         WHERE evento_id = ? AND sector_id = ? AND tipo = 'NO_NUMERADO'
                        """, item.getCantidad(), reserva.getEventoId(), item.getSectorId());
            }
        }
        reserva.setEstado(estadoDestino);
        reservaRepository.save(reserva);
        return true;
    }

    private void registrarVenta(UUID eventoId, UUID clienteId, int entradas) {
        if (entradas <= 0) {
            return;
        }
        ClienteEventoId id = new ClienteEventoId(eventoId, clienteId);
        ClienteEvento clienteEvento = clienteEventoRepository.findById(id)
                .orElseGet(() -> {
                    ClienteEvento nuevo = new ClienteEvento();
                    nuevo.setId(id);
                    nuevo.setEntradasVendidas(0);
                    return nuevo;
                });
        clienteEvento.setEntradasVendidas(clienteEvento.getEntradasVendidas() + entradas);
        clienteEvento.setUpdatedAt(OffsetDateTime.now(clock));
        clienteEventoRepository.save(clienteEvento);
    }

    private ReservationResponse toResponse(Reserva reserva) {
        EventoInventario evento = eventoRepository.findById(reserva.getEventoId())
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "No existe el evento de la reserva"));
        Map<UUID, SectorInventario> sectores = new LinkedHashMap<>();
        sectorRepository.findByEventoIdOrderByNombreAsc(reserva.getEventoId())
                .forEach(sector -> sectores.put(sector.getSectorId(), sector));
        return toResponse(reserva, evento, sectores, reservaItemRepository.findByReservaId(reserva.getId()));
    }

    private ReservationResponse toResponse(Reserva reserva, EventoInventario evento,
                                           Map<UUID, SectorInventario> sectores, List<ReservaItem> items) {
        List<ReservationResponse.Item> respuesta = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ReservaItem item : items) {
            SectorInventario sector = sectores.get(item.getSectorId());
            BigDecimal precio = sector == null ? BigDecimal.ZERO : sector.getPrecio();
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(item.getCantidad()));
            total = total.add(subtotal);
            respuesta.add(new ReservationResponse.Item(
                    item.getSectorId(), item.getAsientoId(), item.getCantidad(), precio, subtotal));
        }
        return new ReservationResponse(
                reserva.getId(),
                reserva.getEventoId(),
                evento.getNombre(),
                ar.edu.unicen.ticketya.inventory.web.dto.Fechas.argentina(evento.getFecha()),
                reserva.getClienteId(),
                reserva.getEstado(),
                ar.edu.unicen.ticketya.inventory.web.dto.Fechas.argentina(reserva.getExpiresAt()),
                respuesta,
                total);
    }

    private Reserva buscarReserva(UUID reservaId) {
        return reservaRepository.findById(reservaId)
                .orElseThrow(() -> ApiException.notFound("RESERVATION_NOT_FOUND",
                        "No existe la reserva " + reservaId));
    }

    private ApiException validacion(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud tiene campos inválidos", List.of(Map.of("field", field, "message", message)));
    }
}
