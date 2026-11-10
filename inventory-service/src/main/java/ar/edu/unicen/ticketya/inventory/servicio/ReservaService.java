package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.dominio.AsientoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoAsiento;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.Reserva;
import ar.edu.unicen.ticketya.inventory.dominio.ReservaItem;
import ar.edu.unicen.ticketya.inventory.dominio.SectorInventario;
import ar.edu.unicen.ticketya.inventory.dominio.TipoSectorInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.AsientoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaItemRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.SectorInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private final JdbcTemplate jdbcTemplate;
    private final LimitePorCliente limitePorCliente;
    private final VencimientoService vencimientoService;
    private final DisponibilidadSincronizador disponibilidadSincronizador;
    private final Clock clock;

    public ReservaService(EventoInventarioRepository eventoRepository,
                          SectorInventarioRepository sectorRepository,
                          AsientoInventarioRepository asientoRepository,
                          ReservaRepository reservaRepository,
                          ReservaItemRepository reservaItemRepository,
                          JdbcTemplate jdbcTemplate,
                          LimitePorCliente limitePorCliente,
                          VencimientoService vencimientoService,
                          DisponibilidadSincronizador disponibilidadSincronizador,
                          Clock clock) {
        this.eventoRepository = eventoRepository;
        this.sectorRepository = sectorRepository;
        this.asientoRepository = asientoRepository;
        this.reservaRepository = reservaRepository;
        this.reservaItemRepository = reservaItemRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.limitePorCliente = limitePorCliente;
        this.vencimientoService = vencimientoService;
        this.disponibilidadSincronizador = disponibilidadSincronizador;
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
        if (evento.getFecha() != null && evento.getFecha().isBefore(OffsetDateTime.now(clock))) {
            throw ApiException.conflict("EVENT_FINISHED", "El evento ya finalizó");
        }

        Map<UUID, SectorInventario> sectores = new LinkedHashMap<>();
        List<ReservaRequest.Item> itemsNumerados = new ArrayList<>();
        List<ReservaRequest.Item> itemsZona = new ArrayList<>();
        Set<UUID> asientosPedidos = new HashSet<>();
        Map<UUID, UUID> sectorDeclarado = new LinkedHashMap<>();

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
                    sectorDeclarado.put(asientoId, item.sectorId());
                }
                itemsNumerados.add(item);
            } else {
                if (item.cantidad() == null || item.cantidad() < 1
                        || (item.asientoIds() != null && !item.asientoIds().isEmpty())) {
                    throw validacion("items", "un ítem de zona requiere cantidad y no admite asientoIds");
                }
                itemsZona.add(item);
            }
        }

        int cantidadPedida = asientosPedidos.size();
        for (ReservaRequest.Item item : itemsZona) {
            cantidadPedida += item.cantidad();
        }
        limitePorCliente.verificar(evento.getEventoId(), clienteId, evento.getLimitePorCliente(), cantidadPedida);

        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusMinutes(MINUTOS_VIGENCIA);
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
                if (!asiento.getSectorId().equals(sectorDeclarado.get(asiento.getAsientoId()))) {
                    throw ApiException.badRequest("SECTOR_MISMATCH",
                            "El asiento " + asiento.getAsientoId() + " no pertenece al sector indicado");
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
        disponibilidadSincronizador.sincronizar(evento.getEventoId());

        return toResponse(reserva, evento, sectores, reservaItems);
    }

    @Transactional
    public ReservationResponse obtener(UUID reservaId, UUID clienteId) {
        Reserva reserva = buscarReserva(reservaId);
        if (!reserva.getClienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_RESERVATION_OWNER", "La reserva pertenece a otro cliente");
        }
        marcarVencidaSiCorresponde(reserva);
        return toResponse(reserva);
    }

    @Transactional
    public ReservationResponse obtenerInterno(UUID reservaId) {
        Reserva reserva = buscarReserva(reservaId);
        marcarVencidaSiCorresponde(reserva);
        return toResponse(reserva);
    }

    /**
     * La usa el pago (confirmación, tarea 3.6). Si la reserva ya venció, libera los lugares y responde 410.
     */
    @Transactional
    public void validarParaPago(UUID reservaId) {
        Reserva reserva = buscarReserva(reservaId);
        boolean acabaDeVencer = vencimientoService.liberarSiVencio(reserva.getId());
        if (acabaDeVencer || reserva.getEstado() == EstadoReserva.VENCIDA) {
            throw ApiException.gone("RESERVATION_EXPIRED", "La reserva está vencida y no se puede pagar");
        }
    }

    private void marcarVencidaSiCorresponde(Reserva reserva) {
        if (vencimientoService.liberarSiVencio(reserva.getId())) {
            reserva.setEstado(EstadoReserva.VENCIDA);
        }
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
