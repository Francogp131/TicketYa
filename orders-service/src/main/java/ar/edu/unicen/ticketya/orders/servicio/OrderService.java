package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OrderService {

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final EntradaRepository entradaRepository;
    private final InventoryClient inventoryClient;
    private final Clock clock;

    public OrderService(OrdenRepository ordenRepository,
                        OrdenItemRepository ordenItemRepository,
                        EntradaRepository entradaRepository,
                        InventoryClient inventoryClient,
                        Clock clock) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.entradaRepository = entradaRepository;
        this.inventoryClient = inventoryClient;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse crear(CrearOrdenRequest request, UUID clienteId, String idempotencyKey) {
        validarIdempotencyKey(idempotencyKey);

        ReservationDto reserva;
        try {
            reserva = inventoryClient.obtenerReserva(request.reservaId());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw ApiException.notFound("RESERVATION_NOT_FOUND", "No existe la reserva " + request.reservaId());
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                    "No se pudo consultar la reserva en inventory", List.of());
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                    "No se pudo consultar la reserva en inventory", List.of());
        }
        if (reserva == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                    "inventory devolvió una respuesta vacía", List.of());
        }
        if (!reserva.clienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_RESERVATION_OWNER", "La reserva pertenece a otro cliente");
        }
        if (!"VIGENTE".equals(reserva.estado())
                || !reserva.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw ApiException.gone("RESERVATION_EXPIRED", "La reserva está vencida y no se puede pagar");
        }

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
        ordenItemRepository.saveAll(items);

        return toResponse(orden, items, List.of());
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

    private OrderResponse toResponse(Orden orden, List<OrdenItem> items, List<Entrada> entradas) {
        List<OrderResponse.Item> itemsResponse = items.stream()
                .map(item -> new OrderResponse.Item(item.getSectorId(), item.getAsientoId(),
                        item.getCantidad(), item.getPrecioUnitario(), item.getSubtotal()))
                .toList();
        List<OrderResponse.Entrada> entradasResponse = entradas.stream()
                .map(entrada -> new OrderResponse.Entrada(entrada.getId(), entrada.getQrCode(),
                        entrada.getEstado().name(), entrada.getSectorId(), entrada.getAsientoId()))
                .toList();
        return new OrderResponse(
                orden.getId(),
                orden.getClienteId(),
                orden.getReservaId(),
                orden.getEventoId(),
                orden.getEventoNombre(),
                aArgentina(orden.getEventoFecha()),
                orden.getEstado(),
                orden.getMedioPago(),
                orden.getTotal(),
                itemsResponse,
                entradasResponse,
                aArgentina(orden.getCreatedAt()));
    }

    private OffsetDateTime aArgentina(OffsetDateTime fecha) {
        return fecha == null ? null
                : fecha.atZoneSameInstant(ZoneId.of("America/Argentina/Buenos_Aires")).toOffsetDateTime();
    }

    private void validarIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La solicitud tiene campos inválidos",
                    List.of(java.util.Map.of("field", "Idempotency-Key",
                            "message", "es obligatoria y debe tener entre 8 y 100 caracteres")));
        }
    }
}
