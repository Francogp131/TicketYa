package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite las entradas con QR único (tarea 4.4). Participa de la transacción de
 * la compra para que las entradas puedan referenciar la orden recién creada.
 */
@Service
public class TicketService {

    private final EntradaRepository entradaRepository;

    public TicketService(EntradaRepository entradaRepository) {
        this.entradaRepository = entradaRepository;
    }

    @Transactional
    public List<Entrada> emitir(Orden orden, List<OrdenItem> items) {
        List<Entrada> entradas = new ArrayList<>();
        for (OrdenItem item : items) {
            if (item.getAsientoId() != null) {
                entradas.add(nuevaEntrada(orden, item, item.getAsientoId()));
            } else {
                for (int i = 0; i < item.getCantidad(); i++) {
                    entradas.add(nuevaEntrada(orden, item, null));
                }
            }
        }
        return entradaRepository.saveAll(entradas);
    }

    private Entrada nuevaEntrada(Orden orden, OrdenItem item, UUID asientoId) {
        Entrada entrada = new Entrada();
        entrada.setOrdenId(orden.getId());
        entrada.setOrdenItemId(item.getId());
        entrada.setClienteId(orden.getClienteId());
        entrada.setEventoId(orden.getEventoId());
        entrada.setEventoNombre(orden.getEventoNombre());
        entrada.setEventoFecha(orden.getEventoFecha());
        entrada.setSectorId(item.getSectorId());
        entrada.setAsientoId(asientoId);
        entrada.setQrCode(UUID.randomUUID());
        return entrada;
    }
}
