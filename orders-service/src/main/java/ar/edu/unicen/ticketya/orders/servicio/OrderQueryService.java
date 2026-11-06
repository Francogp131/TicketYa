package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import ar.edu.unicen.ticketya.orders.web.dto.PageResponse;
import ar.edu.unicen.ticketya.orders.web.dto.TicketResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de órdenes y entradas propias (tarea 4.10). Siempre filtra por el
 * cliente autenticado: un ADMIN tampoco ve los datos de otro cliente.
 */
@Service
public class OrderQueryService {

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final EntradaRepository entradaRepository;

    public OrderQueryService(OrdenRepository ordenRepository,
                             OrdenItemRepository ordenItemRepository,
                             EntradaRepository entradaRepository) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.entradaRepository = entradaRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> ordenes(UUID clienteId, int page, int size) {
        validarPagina(page, size);
        Page<Orden> ordenes = ordenRepository.findByClienteIdOrderByCreatedAtDesc(
                clienteId, PageRequest.of(page, size));
        List<OrderResponse> content = ordenes.getContent().stream()
                .map(orden -> OrderResponse.from(orden,
                        ordenItemRepository.findByOrdenId(orden.getId()),
                        entradaRepository.findByOrdenId(orden.getId())))
                .toList();
        return new PageResponse<>(content, page, size,
                ordenes.getTotalElements(), ordenes.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> entradas(UUID clienteId, UUID eventoId, int page, int size) {
        validarPagina(page, size);
        Page<Entrada> entradas = eventoId == null
                ? entradaRepository.findByClienteIdOrderByCreatedAtDesc(
                        clienteId, PageRequest.of(page, size))
                : entradaRepository.findByClienteIdAndEventoIdOrderByCreatedAtDesc(
                        clienteId, eventoId, PageRequest.of(page, size));
        return new PageResponse<>(entradas.getContent().stream()
                .map(TicketResponse::from)
                .toList(), page, size, entradas.getTotalElements(), entradas.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TicketResponse entrada(UUID entradaId, UUID clienteId) {
        Entrada entrada = entradaRepository.findById(entradaId)
                .orElseThrow(() -> ApiException.notFound("TICKET_NOT_FOUND",
                        "No existe la entrada " + entradaId));
        if (!entrada.getClienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_TICKET_OWNER", "La entrada pertenece a otro cliente");
        }
        return TicketResponse.from(entrada);
    }

    private void validarPagina(int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "page debe ser mayor o igual a 0");
        }
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("VALIDATION_ERROR", "size debe estar entre 1 y 100");
        }
    }
}
