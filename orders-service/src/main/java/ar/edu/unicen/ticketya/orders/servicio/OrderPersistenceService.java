package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste la orden, sus ítems y el pago en una transacción corta que se confirma
 * antes de llamar a la pasarela. Así un fallo posterior (emisión, idempotencia)
 * no borra el registro del cobro.
 */
@Service
public class OrderPersistenceService {

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final PagoRepository pagoRepository;

    public OrderPersistenceService(OrdenRepository ordenRepository,
                                   OrdenItemRepository ordenItemRepository,
                                   PagoRepository pagoRepository) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.pagoRepository = pagoRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrdenCreada crearPendiente(ReservationDto reserva, UUID clienteId, CrearOrdenRequest request) {
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

        return new OrdenCreada(orden, items, pago);
    }

    public record OrdenCreada(Orden orden, List<OrdenItem> items, Pago pago) {
    }
}
