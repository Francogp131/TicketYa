package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Devolucion;
import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoDevolucion;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.repositorio.DevolucionRepository;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.ReturnResponse;
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
    private final InventoryClient inventoryClient;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public DevolucionService(DevolucionRepository devolucionRepository,
                             EntradaRepository entradaRepository,
                             PagoRepository pagoRepository,
                             InventoryClient inventoryClient,
                             PaymentGateway paymentGateway,
                             Clock clock) {
        this.devolucionRepository = devolucionRepository;
        this.entradaRepository = entradaRepository;
        this.pagoRepository = pagoRepository;
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
     * estado PROCESANDO si inventory falla, para que el reintento sea posible.
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

        try {
            inventoryClient.devolverItems(new ReturnDto(
                    entrada.getEventoId(),
                    devolucion.getId(),
                    List.of(new ReturnDto.Item(entrada.getSectorId(), entrada.getAsientoId(), 1))));
        } catch (RuntimeException ex) {
            throw ApiException.unavailable("INVENTORY_UNAVAILABLE",
                    "No se pudo liberar el lugar en inventory; la devolución queda PROCESANDO");
        }

        reintegrarPagos(entrada.getOrdenId());
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

    private void reintegrarPagos(UUID ordenId) {
        for (Pago pago : pagoRepository.findByOrdenId(ordenId)) {
            if (pago.getEstado() == EstadoPago.APROBADO) {
                pago.setEstado(EstadoPago.REINTEGRADO);
                pagoRepository.save(pago);
                try {
                    paymentGateway.reintegrar(pago.getId());
                } catch (RuntimeException ex) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                            "No se pudo solicitar el reintegro a la pasarela");
                }
            }
        }
    }
}
