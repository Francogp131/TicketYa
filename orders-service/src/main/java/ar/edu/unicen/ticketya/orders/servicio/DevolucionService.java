package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Devolucion;
import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoDevolucion;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.DevolucionRepository;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.ReturnResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Devoluciones de entradas (tareas 4.8 y soporte de 4.9).
 *
 * <p>La solicitud queda PENDIENTE. La aprobación libera el lugar en inventory
 * (idempotente por id de devolución), invalida la entrada y solicita el
 * reintegro a la pasarela. El rechazo mantiene la entrada VALIDA.</p>
 */
@Service
public class DevolucionService {

    private static final long HORAS_MINIMAS = 72;

    private final DevolucionRepository devolucionRepository;
    private final EntradaRepository entradaRepository;
    private final PagoRepository pagoRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final ReintegroRepository reintegroRepository;
    private final InventoryClient inventoryClient;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public DevolucionService(DevolucionRepository devolucionRepository,
                             EntradaRepository entradaRepository,
                             PagoRepository pagoRepository,
                             OrdenItemRepository ordenItemRepository,
                             ReintegroRepository reintegroRepository,
                             InventoryClient inventoryClient,
                             PaymentGateway paymentGateway,
                             Clock clock) {
        this.devolucionRepository = devolucionRepository;
        this.entradaRepository = entradaRepository;
        this.pagoRepository = pagoRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.reintegroRepository = reintegroRepository;
        this.inventoryClient = inventoryClient;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    @Transactional
    public ReturnResponse solicitar(UUID entradaId, UUID clienteId, String motivo) {
        Entrada entrada = entradaRepository.findById(entradaId)
                .orElseThrow(() -> ApiException.notFound("TICKET_NOT_FOUND", "No existe la entrada " + entradaId));
        if (!entrada.getClienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_TICKET_OWNER", "La entrada pertenece a otro cliente");
        }
        if (entrada.getEstado() != EstadoEntrada.VALIDA) {
            throw ApiException.conflict("TICKET_NOT_VALID", "La entrada no está válida para devolución");
        }
        OffsetDateTime limite = entrada.getEventoFecha().minusHours(HORAS_MINIMAS);
        if (!OffsetDateTime.now(clock).isBefore(limite)) {
            throw ApiException.unprocessable("RETURN_WINDOW_CLOSED",
                    "Solo se puede devolver hasta 72 horas antes del evento");
        }
        if (devolucionRepository.existsByEntradaIdAndEstadoIn(entradaId,
                List.of(EstadoDevolucion.PENDIENTE, EstadoDevolucion.PROCESANDO))) {
            throw ApiException.conflict("RETURN_ALREADY_EXISTS", "La entrada ya tiene una devolución abierta");
        }

        Devolucion devolucion = new Devolucion();
        devolucion.setEntradaId(entradaId);
        devolucion.setEstado(EstadoDevolucion.PENDIENTE);
        devolucion.setMotivo(motivo == null ? null : motivo.trim());
        devolucion.setCreatedAt(OffsetDateTime.now(clock));
        devolucion = devolucionRepository.save(devolucion);
        return ReturnResponse.from(devolucion);
    }

    /**
     * La aprobación se ejecuta en una transacción que confirma también el
     * estado PROCESANDO si inventory o la pasarela fallan, para que el reintento sea posible.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public ReturnResponse aprobar(UUID devolucionId) {
        Devolucion devolucion = buscar(devolucionId);
        if (devolucion.getEstado() == EstadoDevolucion.APROBADA
                || devolucion.getEstado() == EstadoDevolucion.RECHAZADA) {
            throw ApiException.conflict("RETURN_ALREADY_RESOLVED", "La devolución ya fue resuelta");
        }
        UUID entradaId = devolucion.getEntradaId();
        Entrada entrada = entradaRepository.findById(entradaId)
                .orElseThrow(() -> ApiException.notFound("TICKET_NOT_FOUND",
                        "No existe la entrada " + entradaId));
        if (entrada.getEstado() == EstadoEntrada.UTILIZADA) {
            throw ApiException.conflict("TICKET_NOT_VALID", "La entrada ya fue utilizada");
        }

        devolucion.setEstado(EstadoDevolucion.PROCESANDO);
        devolucionRepository.save(devolucion);

        // La entrada deja de ser válida mientras dura la devolución. Si el reintegro
        // o inventory fallan, queda EN_DEVOLUCION y el retry service la termina.
        entrada.setEstado(EstadoEntrada.EN_DEVOLUCION);
        entradaRepository.save(entrada);

        // Procesar reintegro antes de liberar el asiento para evitar inconsistencia
        reintegrarEntrada(entrada);

        try {
            inventoryClient.devolverItems(new ReturnDto(
                    entrada.getEventoId(),
                    devolucion.getId(),
                    List.of(new ReturnDto.Item(entrada.getSectorId(), entrada.getAsientoId(), 1)),
                    entrada.getClienteId()));
        } catch (RuntimeException ex) {
            throw ApiException.unavailable("INVENTORY_UNAVAILABLE",
                    "No se pudo liberar el lugar en inventory; la devolución queda PROCESANDO");
        }

        entrada.setEstado(EstadoEntrada.DEVUELTA);
        entradaRepository.save(entrada);
        devolucion.setEstado(EstadoDevolucion.APROBADA);
        devolucion.setResolvedAt(OffsetDateTime.now(clock));
        devolucion = devolucionRepository.save(devolucion);
        return ReturnResponse.from(devolucion);
    }

    @Transactional
    public ReturnResponse rechazar(UUID devolucionId) {
        Devolucion devolucion = buscar(devolucionId);
        if (devolucion.getEstado() == EstadoDevolucion.APROBADA
                || devolucion.getEstado() == EstadoDevolucion.RECHAZADA) {
            throw ApiException.conflict("RETURN_ALREADY_RESOLVED", "La devolución ya fue resuelta");
        }
        if (devolucion.getEstado() == EstadoDevolucion.PROCESANDO) {
            throw ApiException.conflict("RETURN_IN_PROGRESS",
                    "La devolución está procesando el reintegro y no se puede rechazar");
        }
        devolucion.setEstado(EstadoDevolucion.RECHAZADA);
        devolucion.setResolvedAt(OffsetDateTime.now(clock));
        devolucion = devolucionRepository.save(devolucion);
        return ReturnResponse.from(devolucion);
    }

    private Devolucion buscar(UUID devolucionId) {
        return devolucionRepository.findById(devolucionId)
                .orElseThrow(() -> ApiException.notFound("RETURN_NOT_FOUND",
                        "No existe la devolución " + devolucionId));
    }

    private void reintegrarEntrada(Entrada entrada) {
        Pago pago = pagoRepository.findByOrdenId(entrada.getOrdenId()).stream()
                .filter(p -> p.getEstado() == EstadoPago.APROBADO
                        || p.getEstado() == EstadoPago.REINTEGRO_PENDIENTE
                        || p.getEstado() == EstadoPago.PARCIALMENTE_REINTEGRADO)
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("PAYMENT_NOT_REFUNDABLE",
                        "No hay un pago aprobado para reintegrar"));

        OrdenItem item = ordenItemRepository.findById(entrada.getOrdenItemId())
                .orElseThrow(() -> ApiException.notFound("ORDER_ITEM_NOT_FOUND",
                        "No existe el ítem de orden " + entrada.getOrdenItemId()));
        BigDecimal monto = item.getPrecioUnitario();

        Reintegro reintegro = reintegroRepository.findByPagoId(pago.getId()).stream()
                .filter(r -> entrada.getId().equals(r.getEntradaId()))
                .findFirst()
                .orElseGet(() -> reintegroRepository.save(
                        new Reintegro(pago.getId(), entrada.getId(), monto, EstadoReintegro.PENDIENTE)));

        if (reintegro.getEstado() != EstadoReintegro.APROBADO) {
            try {
                paymentGateway.reintegrar(pago.getId(), reintegro.getMonto());
                reintegro.setEstado(EstadoReintegro.APROBADO);
                reintegroRepository.save(reintegro);
            } catch (RuntimeException ex) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                        "No se pudo solicitar el reintegro a la pasarela; la devolución queda PROCESANDO");
            }
        }

        actualizarEstadoPago(pago, entrada.getOrdenId());
    }

    private void actualizarEstadoPago(Pago pago, UUID ordenId) {
        List<Reintegro> reintegros = reintegroRepository.findByPagoId(pago.getId());
        BigDecimal totalAprobado = reintegros.stream()
                .filter(r -> r.getEstado() == EstadoReintegro.APROBADO)
                .map(Reintegro::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hayPendientes = reintegros.stream().anyMatch(r -> r.getEstado() == EstadoReintegro.PENDIENTE);
        if (totalAprobado.compareTo(pago.getMonto()) >= 0) {
            pago.setEstado(EstadoPago.REINTEGRADO);
        } else if (hayPendientes) {
            pago.setEstado(EstadoPago.REINTEGRO_PENDIENTE);
        } else if (totalAprobado.compareTo(BigDecimal.ZERO) > 0) {
            pago.setEstado(EstadoPago.PARCIALMENTE_REINTEGRADO);
        }
        pagoRepository.save(pago);
    }
}
