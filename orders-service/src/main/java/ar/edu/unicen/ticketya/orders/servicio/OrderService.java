package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.CancelacionEventoResponse;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OrderService {

    private static final List<String> TOKENS_VALIDOS = List.of("APROBADO", "RECHAZADO", "LENTO");

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final EntradaRepository entradaRepository;
    private final PagoRepository pagoRepository;
    private final ReintegroRepository reintegroRepository;
    private final InventoryClient inventoryClient;
    private final PaymentGateway paymentGateway;
    private final TicketService ticketService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public OrderService(OrdenRepository ordenRepository,
                        OrdenItemRepository ordenItemRepository,
                        EntradaRepository entradaRepository,
                        PagoRepository pagoRepository,
                        ReintegroRepository reintegroRepository,
                        InventoryClient inventoryClient,
                        PaymentGateway paymentGateway,
                        TicketService ticketService,
                        IdempotencyService idempotencyService,
                        Clock clock) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.entradaRepository = entradaRepository;
        this.pagoRepository = pagoRepository;
        this.reintegroRepository = reintegroRepository;
        this.inventoryClient = inventoryClient;
        this.paymentGateway = paymentGateway;
        this.ticketService = ticketService;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    /**
     * Crea la orden, cobra y emite entradas (tareas 4.3 a 4.7).
     *
     * <p>La Idempotency-Key se reclama antes de tocar inventory. Si la compra
     * falla, el reclamo se libera para permitir un reintento limpio.</p>
     */
    @Transactional(noRollbackFor = ApiException.class)
    public OrderResponse crear(CrearOrdenRequest request, UUID clienteId, String idempotencyKey) {
        validarIdempotencyKey(idempotencyKey);
        validarToken(request.token());

        String hash = idempotencyService.hash(request.reservaId().toString(),
                request.medioPago().name(), request.token());
        try {
            idempotencyService.registrar(idempotencyKey, hash, OffsetDateTime.now(clock));
        } catch (DataIntegrityViolationException ex) {
            try {
                return idempotencyService.resolverExistente(idempotencyKey, hash);
            } catch (ApiException apiEx) {
                if ("IDEMPOTENCY_KEY_EXPIRED".equals(apiEx.getCode())) {
                    idempotencyService.registrar(idempotencyKey, hash, OffsetDateTime.now(clock));
                } else {
                    throw apiEx;
                }
            }
        }

        try {
            OrderResponse response = ejecutarCompra(request, clienteId);
            idempotencyService.completar(idempotencyKey, response);
            return response;
        } catch (RuntimeException ex) {
            try {
                idempotencyService.liberar(idempotencyKey);
            } catch (RuntimeException ignored) {
                // best-effort: no ocultar la excepción original
            }
            throw ex;
        }
    }

    private OrderResponse ejecutarCompra(CrearOrdenRequest request, UUID clienteId) {
        ReservationDto reserva = consultarReserva(request.reservaId());
        validarReserva(reserva, clienteId);

        BigDecimal total = reserva.items().stream()
                .map(ReservationDto.Item::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Orden orden = new Orden();
        orden.setClienteId(clienteId);
        orden.setReservaId(reserva.id());
        orden.setEventoId(reserva.eventoId());
        orden.setEventoNombre(reserva.eventoNombre());
        orden.setEventoFecha(reserva.eventoFecha());
        orden.setEstado(EstadoOrden.PENDIENTE_PAGO);
        orden.setMedioPago(request.medioPago());
        orden.setTotal(total);
        orden = ordenRepository.save(orden);

        List<OrdenItem> items = new ArrayList<>();
        for (ReservationDto.Item item : reserva.items()) {
            items.add(new OrdenItem(orden.getId(), item.sectorId(), item.asientoId(),
                    item.cantidad(), item.precioUnitario(), item.subtotal()));
        }
        items = ordenItemRepository.saveAll(items);

        Pago pago = new Pago();
        pago.setOrdenId(orden.getId());
        pago.setEstado(EstadoPago.PENDIENTE);
        pago.setMonto(total);
        pago.setMedioPago(request.medioPago());
        pago.setToken(request.token());
        pago = pagoRepository.save(pago);

        PaymentGateway.PaymentResult resultado;
        try {
            resultado = paymentGateway.cobrar(orden.getId(), total, request.medioPago(), request.token());
        } catch (PaymentGatewayException ex) {
            orden.setEstado(EstadoOrden.ERROR_PAGO);
            pago.setEstado(EstadoPago.RECHAZADO);
            ordenRepository.save(orden);
            pagoRepository.save(pago);
            liberarSinFallo(reserva.id(), "ERROR_PAGO");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                    "La pasarela de pagos falló o no respondió a tiempo");
        }

        pago.setExternalId(resultado.externalId());
        if (resultado.estado() == EstadoPago.RECHAZADO) {
            orden.setEstado(EstadoOrden.RECHAZADA);
            pago.setEstado(EstadoPago.RECHAZADO);
            ordenRepository.save(orden);
            pagoRepository.save(pago);
            liberarSinFallo(reserva.id(), "PAGO_RECHAZADO");
            return toResponse(orden, items, List.of());
        }

        pago.setEstado(EstadoPago.APROBADO);
        pagoRepository.save(pago);

        try {
            inventoryClient.confirmarReserva(reserva.id());
        } catch (RuntimeException ex) {
            return compensar(orden, pago, items, reserva, false);
        }

        orden.setEstado(EstadoOrden.PAGADA);
        ordenRepository.save(orden);
        try {
            List<Entrada> entradas = ticketService.emitir(orden, items);
            return toResponse(orden, items, entradas);
        } catch (RuntimeException ex) {
            return compensar(orden, pago, items, reserva, true);
        }
    }

    /**
     * Compensación (tarea 4.7): la orden nunca queda PAGADA. Se crea el reintegro
     * pendiente y se intenta solicitarlo a la pasarela; si falla, queda REINTEGRO_PENDIENTE.
     */
    private OrderResponse compensar(Orden orden, Pago pago, List<OrdenItem> items,
                                    ReservationDto reserva, boolean ventaConfirmada) {
        orden.setEstado(EstadoOrden.REINTEGRO_PENDIENTE);
        ordenRepository.save(orden);
        if (ventaConfirmada) {
            devolverSinFallo(reserva, items, pago.getId());
        } else {
            liberarSinFallo(reserva.id(), "ERROR_CONFIRMACION");
        }
        solicitarReintegroCompensacion(pago);
        return toResponse(orden, items, List.of());
    }

    private void solicitarReintegroCompensacion(Pago pago) {
        Reintegro reintegro = reintegroRepository.findByPagoId(pago.getId()).stream()
                .filter(r -> r.getEntradaId() == null)
                .findFirst()
                .orElseGet(() -> reintegroRepository.save(
                        new Reintegro(pago.getId(), null, pago.getMonto(), EstadoReintegro.PENDIENTE)));
        if (reintegro.getEstado() != EstadoReintegro.APROBADO) {
            try {
                paymentGateway.reintegrar(pago.getId(), reintegro.getMonto());
                reintegro.setEstado(EstadoReintegro.APROBADO);
                reintegroRepository.save(reintegro);
            } catch (RuntimeException ex) {
                // Queda pendiente para reintento.
            }
        }
        actualizarEstadoPago(pago);
    }

    private ReservationDto consultarReserva(UUID reservaId) {
        try {
            ReservationDto reserva = inventoryClient.obtenerReserva(reservaId);
            if (reserva == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                        "inventory devolvió una respuesta vacía");
            }
            return reserva;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw ApiException.notFound("RESERVATION_NOT_FOUND", "No existe la reserva " + reservaId);
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                    "No se pudo consultar la reserva en inventory");
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                    "No se pudo consultar la reserva en inventory");
        }
    }

    private void validarReserva(ReservationDto reserva, UUID clienteId) {
        if (!reserva.clienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_RESERVATION_OWNER", "La reserva pertenece a otro cliente");
        }
        if (!"VIGENTE".equals(reserva.estado())
                || !reserva.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw ApiException.gone("RESERVATION_EXPIRED", "La reserva está vencida y no se puede pagar");
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse obtener(UUID ordenId, UUID clienteId) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No existe la orden " + ordenId));
        if (!orden.getClienteId().equals(clienteId)) {
            throw ApiException.forbidden("ORDER_NOT_OWNER", "La orden pertenece a otro cliente");
        }
        return toResponse(orden, ordenItemRepository.findByOrdenId(ordenId),
                entradaRepository.findByOrdenId(ordenId));
    }

    /**
     * Cancelación de evento pedida por catalog (tarea 4.9, necesaria para 2.3).
     * Idempotente: repetir devuelve la misma cantidad de entradas en reintegro
     * y no vuelve a pedir reintegros ya aprobados.
     */
    @Transactional
    public CancelacionEventoResponse cancelarEvento(UUID eventoId) {
        List<Entrada> validas = entradaRepository.findByEventoIdAndEstado(eventoId, EstadoEntrada.VALIDA);
        validas.forEach(entrada -> entrada.setEstado(EstadoEntrada.EN_REINTEGRO));
        entradaRepository.saveAll(validas);

        List<Entrada> enReintegro = entradaRepository.findByEventoIdAndEstado(eventoId, EstadoEntrada.EN_REINTEGRO);
        int reintegrosAprobados = 0;
        for (Entrada entrada : enReintegro) {
            Pago pago = pagoRepository.findByOrdenId(entrada.getOrdenId()).stream()
                    .filter(p -> p.getEstado() == EstadoPago.APROBADO
                            || p.getEstado() == EstadoPago.REINTEGRO_PENDIENTE
                            || p.getEstado() == EstadoPago.REINTEGRADO)
                    .findFirst()
                    .orElse(null);
            if (pago == null) {
                continue;
            }
            OrdenItem item = ordenItemRepository.findById(entrada.getOrdenItemId()).orElse(null);
            if (item == null) {
                continue;
            }
            BigDecimal monto = item.getPrecioUnitario();
            Reintegro reintegro = reintegroRepository.findByPagoId(pago.getId()).stream()
                    .filter(r -> entrada.getId().equals(r.getEntradaId()))
                    .findFirst()
                    .orElseGet(() -> reintegroRepository.save(
                            new Reintegro(pago.getId(), entrada.getId(), monto, EstadoReintegro.PENDIENTE)));
            if (reintegro.getEstado() != EstadoReintegro.APROBADO) {
                try {
                    paymentGateway.reintegrar(pago.getId(), monto);
                    reintegro.setEstado(EstadoReintegro.APROBADO);
                    reintegroRepository.save(reintegro);
                } catch (RuntimeException ex) {
                    // Queda PENDIENTE para reintento.
                }
            }
            if (reintegro.getEstado() == EstadoReintegro.APROBADO) {
                reintegrosAprobados++;
            }
            actualizarEstadoPago(pago);
        }
        return new CancelacionEventoResponse(eventoId, enReintegro.size(), reintegrosAprobados);
    }

    private void actualizarEstadoPago(Pago pago) {
        List<Reintegro> reintegros = reintegroRepository.findByPagoId(pago.getId());
        BigDecimal totalAprobado = reintegros.stream()
                .filter(r -> r.getEstado() == EstadoReintegro.APROBADO)
                .map(Reintegro::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hayPendientes = reintegros.stream().anyMatch(r -> r.getEstado() == EstadoReintegro.PENDIENTE);
        if (totalAprobado.compareTo(pago.getMonto()) >= 0) {
            pago.setEstado(EstadoPago.REINTEGRADO);
        } else if (totalAprobado.compareTo(BigDecimal.ZERO) > 0 || hayPendientes) {
            pago.setEstado(EstadoPago.REINTEGRO_PENDIENTE);
        }
        pagoRepository.save(pago);
    }

    private void liberarSinFallo(UUID reservaId, String motivo) {
        try {
            inventoryClient.liberarReserva(reservaId, motivo);
        } catch (RuntimeException ex) {
            // La compensación es best-effort; inventory reintenta por su cuenta.
        }
    }

    private void devolverSinFallo(ReservationDto reserva, List<OrdenItem> items, UUID referencia) {
        List<ReturnDto.Item> devoluciones = items.stream()
                .map(item -> new ReturnDto.Item(item.getSectorId(), item.getAsientoId(),
                        item.getAsientoId() == null ? item.getCantidad() : 1))
                .toList();
        try {
            inventoryClient.devolverItems(new ReturnDto(reserva.eventoId(), referencia, devoluciones,
                    reserva.clienteId()));
        } catch (RuntimeException ex) {
            // La orden ya quedó en REINTEGRO_PENDIENTE; se puede reintentar.
        }
    }

    private OrderResponse toResponse(Orden orden, List<OrdenItem> items, List<Entrada> entradas) {
        return OrderResponse.from(orden, items, entradas);
    }

    private void validarIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La solicitud tiene campos inválidos",
                    List.of(Map.of("field", "Idempotency-Key",
                            "message", "es obligatoria y debe tener entre 8 y 100 caracteres")));
        }
    }

    private void validarToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (!TOKENS_VALIDOS.contains(token.trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La solicitud tiene campos inválidos",
                    List.of(Map.of("field", "token",
                            "message", "debe ser APROBADO, RECHAZADO o LENTO")));
        }
    }
}
