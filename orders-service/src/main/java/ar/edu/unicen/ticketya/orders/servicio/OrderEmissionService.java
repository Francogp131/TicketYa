package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite las entradas en una transacción propia. La orden ya fue confirmada y
 * commiteada por {@link OrderPersistenceService}, así que un fallo en la emisión
 * no contamina la transacción principal y la compensación puede persistirse.
 */
@Service
public class OrderEmissionService {

    private final OrdenRepository ordenRepository;
    private final TicketService ticketService;

    public OrderEmissionService(OrdenRepository ordenRepository, TicketService ticketService) {
        this.ordenRepository = ordenRepository;
        this.ticketService = ticketService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Entrada> marcarPagadaYEmitir(Orden orden, List<OrdenItem> items) {
        orden.setEstado(EstadoOrden.PAGADA);
        ordenRepository.save(orden);
        return ticketService.emitir(orden, items);
    }
}
