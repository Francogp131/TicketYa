package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import ar.edu.unicen.ticketya.orders.web.dto.PageResponse;
import ar.edu.unicen.ticketya.orders.web.dto.TicketResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrdenRepository ordenRepository;
    @Mock
    private OrdenItemRepository ordenItemRepository;
    @Mock
    private EntradaRepository entradaRepository;

    private OrderQueryService service;
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(ordenRepository, ordenItemRepository, entradaRepository);
    }

    @Test
    void ordenesDevuelveSoloLasDelClienteYPaginadas() {
        Orden orden = orden();
        when(ordenRepository.findByClienteIdOrderByCreatedAtDesc(clienteId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(orden), PageRequest.of(0, 20), 1));
        when(ordenItemRepository.findByOrdenId(orden.getId())).thenReturn(List.of());
        when(entradaRepository.findByOrdenId(orden.getId())).thenReturn(List.of());

        PageResponse<OrderResponse> pagina = service.ordenes(clienteId, 0, 20);

        assertThat(pagina.content()).singleElement()
                .extracting(OrderResponse::id).isEqualTo(orden.getId());
        assertThat(pagina.totalElements()).isEqualTo(1);
        assertThat(pagina.totalPages()).isEqualTo(1);
        verify(ordenRepository).findByClienteIdOrderByCreatedAtDesc(clienteId, PageRequest.of(0, 20));
    }

    @Test
    void entradasFiltraPorEventoCuandoSeEnviaEventoId() {
        UUID eventoId = UUID.randomUUID();
        Entrada entrada = entrada(eventoId);
        when(entradaRepository.findByClienteIdAndEventoIdOrderByCreatedAtDesc(
                clienteId, eventoId, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(entrada), PageRequest.of(0, 10), 1));

        PageResponse<TicketResponse> pagina = service.entradas(clienteId, eventoId, 0, 10);

        assertThat(pagina.content()).singleElement()
                .extracting(TicketResponse::id).isEqualTo(entrada.getId());
        verify(entradaRepository).findByClienteIdAndEventoIdOrderByCreatedAtDesc(
                clienteId, eventoId, PageRequest.of(0, 10));
    }

    @Test
    void entradaAjenaDevuelve403() {
        Entrada entrada = entrada(UUID.randomUUID());
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));

        assertThatThrownBy(() -> service.entrada(entrada.getId(), UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("NOT_TICKET_OWNER");
                });
    }

    @Test
    void entradaInexistenteDevuelve404() {
        UUID entradaId = UUID.randomUUID();
        when(entradaRepository.findById(entradaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrada(entradaId, clienteId))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("TICKET_NOT_FOUND");
                });
    }

    @Test
    void paginaInvalidaDevuelve400() {
        assertThatThrownBy(() -> service.ordenes(clienteId, -1, 20))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
        assertThatThrownBy(() -> service.entradas(clienteId, null, 0, 101))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
        verify(ordenRepository, never()).findByClienteIdOrderByCreatedAtDesc(any(), any());
        verify(entradaRepository, never()).findByClienteIdOrderByCreatedAtDesc(any(), any());
    }

    private Orden orden() {
        Orden orden = new Orden();
        orden.setId(UUID.randomUUID());
        orden.setClienteId(clienteId);
        orden.setReservaId(UUID.randomUUID());
        orden.setEventoId(UUID.randomUUID());
        orden.setEventoNombre("Recital");
        orden.setEventoFecha(OffsetDateTime.parse("2027-01-15T21:00:00-03:00"));
        orden.setEstado(EstadoOrden.PAGADA);
        orden.setMedioPago(MedioPago.TARJETA);
        orden.setTotal(new BigDecimal("15000.00"));
        return orden;
    }

    private Entrada entrada(UUID eventoId) {
        Entrada entrada = new Entrada();
        entrada.setId(UUID.randomUUID());
        entrada.setClienteId(clienteId);
        entrada.setOrdenId(UUID.randomUUID());
        entrada.setEventoId(eventoId);
        entrada.setEventoNombre("Recital");
        entrada.setEventoFecha(OffsetDateTime.parse("2027-01-15T21:00:00-03:00"));
        entrada.setSectorId(UUID.randomUUID());
        entrada.setAsientoId(UUID.randomUUID());
        entrada.setQrCode(UUID.randomUUID());
        entrada.setEstado(EstadoEntrada.VALIDA);
        return entrada;
    }
}
