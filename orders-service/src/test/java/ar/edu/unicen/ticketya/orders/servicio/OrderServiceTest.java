package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private OrdenRepository ordenRepository;
    @Mock
    private OrdenItemRepository ordenItemRepository;
    @Mock
    private EntradaRepository entradaRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private TicketService ticketService;
    @Mock
    private IdempotencyService idempotencyService;

    private OrderService orderService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZONA);
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(ordenRepository, ordenItemRepository, entradaRepository,
                pagoRepository, inventoryClient, paymentGateway, ticketService,
                idempotencyService, clock);
    }

    @Test
    void crearOrdenDesdeReservaCalculaElTotalEnElServidorYPaga() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);
        stubOrdenGuardada();
        when(paymentGateway.cobrar(any(), any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(EstadoPago.APROBADO, "mock-1"));
        when(ticketService.emitir(any(Orden.class), any())).thenReturn(List.of());

        OrderResponse response = orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, "APROBADO"),
                clienteId, "compra-0001");

        assertThat(response.estado()).isEqualTo(EstadoOrden.PAGADA);
        assertThat(response.total()).isEqualByComparingTo("30000.00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.eventoNombre()).isEqualTo("Recital");
        verify(inventoryClient).confirmarReserva(reserva.id());
        verify(ordenItemRepository).saveAll(any());
    }

    @Test
    void elPagoAprobadoEmiteUnaEntradaPorLugar() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);
        stubOrdenGuardada();
        when(paymentGateway.cobrar(any(), any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(EstadoPago.APROBADO, "mock-1"));
        Entrada primera = entrada(reserva, UUID.randomUUID());
        Entrada segunda = entrada(reserva, UUID.randomUUID());
        when(ticketService.emitir(any(Orden.class), any())).thenReturn(List.of(primera, segunda));

        OrderResponse response = orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, "APROBADO"),
                clienteId, "compra-0006");

        assertThat(response.entradas()).hasSize(2);
        assertThat(response.entradas()).extracting(OrderResponse.Entrada::qrCode)
                .doesNotHaveDuplicates();
    }

    @Test
    void elPagoRechazadoDejaLaOrdenRechazadaSinEntradasYLiberaLaReserva() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);
        stubOrdenGuardada();
        when(paymentGateway.cobrar(any(), any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(EstadoPago.RECHAZADO, "mock-2"));

        OrderResponse response = orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, "RECHAZADO"),
                clienteId, "compra-0007");

        assertThat(response.estado()).isEqualTo(EstadoOrden.RECHAZADA);
        assertThat(response.entradas()).isEmpty();
        verify(inventoryClient).liberarReserva(reserva.id(), "PAGO_RECHAZADO");
        verify(ticketService, never()).emitir(any(), any());
        verify(inventoryClient, never()).confirmarReserva(any());
    }

    @Test
    void elPagoLentoDevuelve502YLiberaLaReserva() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);
        stubOrdenGuardada();
        when(paymentGateway.cobrar(any(), any(), any(), any()))
                .thenThrow(new PaymentGatewayException("timeout"));

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, "LENTO"),
                clienteId, "compra-0008"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(502);
                    assertThat(ex.getCode()).isEqualTo("PAYMENT_GATEWAY_ERROR");
                });
        verify(inventoryClient).liberarReserva(reserva.id(), "ERROR_PAGO");
        verify(idempotencyService).liberar("compra-0008");
    }

    @Test
    void siFallaLaConfirmacionDeInventarioLaOrdenQuedaEnReintegroPendiente() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);
        stubOrdenGuardada();
        when(paymentGateway.cobrar(any(), any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(EstadoPago.APROBADO, "mock-3"));
        org.mockito.Mockito.doThrow(new RuntimeException("inventory caído"))
                .when(inventoryClient).confirmarReserva(reserva.id());

        OrderResponse response = orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, "APROBADO"),
                clienteId, "compra-0009");

        assertThat(response.estado()).isEqualTo(EstadoOrden.REINTEGRO_PENDIENTE);
        assertThat(response.entradas()).isEmpty();
        verify(inventoryClient).liberarReserva(reserva.id(), "ERROR_CONFIRMACION");
        verify(ticketService, never()).emitir(any(), any());
        ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository, org.mockito.Mockito.atLeastOnce()).save(pago.capture());
        assertThat(pago.getValue().getEstado()).isEqualTo(EstadoPago.REINTEGRADO);
    }

    @Test
    void siFallaLaEmisionDeEntradasLaOrdenNoQuedaPagadaYSeDevuelvenLosLugares() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);
        stubOrdenGuardada();
        when(paymentGateway.cobrar(any(), any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(EstadoPago.APROBADO, "mock-4"));
        when(ticketService.emitir(any(Orden.class), any())).thenThrow(new RuntimeException("sin conexión"));

        OrderResponse response = orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, "APROBADO"),
                clienteId, "compra-0010");

        assertThat(response.estado()).isEqualTo(EstadoOrden.REINTEGRO_PENDIENTE);
        assertThat(response.entradas()).isEmpty();
        verify(inventoryClient).devolverItems(any(ReturnDto.class));
        verify(inventoryClient, never()).liberarReserva(any(), anyString());
    }

    @Test
    void unReintentoConLaMismaClaveDevuelveLaRespuestaGuardada() {
        OrderResponse guardada = new OrderResponse(UUID.randomUUID(), clienteId, UUID.randomUUID(),
                UUID.randomUUID(), "Recital", OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                EstadoOrden.PAGADA, MedioPago.TARJETA, new BigDecimal("15000.00"),
                List.of(), List.of(), OffsetDateTime.parse("2026-11-03T12:00:00-03:00"));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicada"))
                .when(idempotencyService).registrar(anyString(), any(), any());
        when(idempotencyService.resolverExistente(anyString(), any())).thenReturn(guardada);

        OrderResponse response = orderService.crear(
                new CrearOrdenRequest(UUID.randomUUID(), MedioPago.TARJETA, "APROBADO"),
                clienteId, "compra-0011");

        assertThat(response).isEqualTo(guardada);
        verify(inventoryClient, never()).obtenerReserva(any());
    }

    @Test
    void unTokenDesconocidoDevuelve400AntesDeConsultarInventory() {
        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(UUID.randomUUID(), MedioPago.TARJETA, "OTRO"),
                clienteId, "compra-0012"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
        verify(inventoryClient, never()).obtenerReserva(any());
    }

    @Test
    void unaReservaAjenaDevuelve403() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, null),
                UUID.randomUUID(), "compra-0002"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("NOT_RESERVATION_OWNER");
                });
    }

    @Test
    void unaReservaInexistenteDevuelve404() {
        UUID reservaId = UUID.randomUUID();
        when(inventoryClient.obtenerReserva(reservaId)).thenThrow(new RestClientResponseException(
                "not found", HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null));

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reservaId, MedioPago.TARJETA, null),
                clienteId, "compra-0003"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("RESERVATION_NOT_FOUND");
                });
    }

    @Test
    void unaReservaVencidaDevuelve410() {
        ReservationDto reserva = reserva("VIGENTE", OffsetDateTime.parse("2026-11-03T11:59:00-03:00"));
        when(inventoryClient.obtenerReserva(reserva.id())).thenReturn(reserva);

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reserva.id(), MedioPago.TARJETA, null),
                clienteId, "compra-0004"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(410);
                    assertThat(ex.getCode()).isEqualTo("RESERVATION_EXPIRED");
                });
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void siInventoryNoRespondeDevuelve502() {
        UUID reservaId = UUID.randomUUID();
        when(inventoryClient.obtenerReserva(reservaId)).thenThrow(new RuntimeException("caído"));

        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(reservaId, MedioPago.TARJETA, null),
                clienteId, "compra-0005"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(502);
                    assertThat(ex.getCode()).isEqualTo("INVENTORY_UNAVAILABLE");
                });
    }

    @Test
    void sinIdempotencyKeyDevuelve400AntesDeConsultarInventory() {
        assertThatThrownBy(() -> orderService.crear(
                new CrearOrdenRequest(UUID.randomUUID(), MedioPago.TARJETA, null),
                clienteId, "corta"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
        verify(inventoryClient, never()).obtenerReserva(any());
    }

    private void stubOrdenGuardada() {
        when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> {
            Orden orden = invocation.getArgument(0);
            if (orden.getId() == null) {
                orden.setId(UUID.randomUUID());
            }
            return orden;
        });
        when(ordenItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> {
            Pago pago = invocation.getArgument(0);
            if (pago.getId() == null) {
                pago.setId(UUID.randomUUID());
            }
            return pago;
        });
    }

    @Test
    void cancelarEventoMarcaEntradasYReintegraLosPagos() {
        UUID eventoId = UUID.randomUUID();
        UUID ordenId = UUID.randomUUID();
        Entrada primera = entradaCancelacion(eventoId, ordenId);
        Entrada segunda = entradaCancelacion(eventoId, ordenId);
        Pago pago = new Pago();
        pago.setId(UUID.randomUUID());
        pago.setOrdenId(ordenId);
        pago.setEstado(EstadoPago.APROBADO);
        when(entradaRepository.findByEventoIdAndEstado(eventoId, EstadoEntrada.VALIDA))
                .thenReturn(List.of(primera, segunda));
        when(entradaRepository.findByEventoIdAndEstado(eventoId, EstadoEntrada.EN_REINTEGRO))
                .thenReturn(List.of(primera, segunda));
        when(pagoRepository.findByOrdenId(ordenId)).thenReturn(List.of(pago));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.cancelarEvento(eventoId);

        assertThat(response.entradasAfectadas()).isEqualTo(2);
        assertThat(response.reintegrosSolicitados()).isEqualTo(1);
        assertThat(primera.getEstado()).isEqualTo(EstadoEntrada.EN_REINTEGRO);
        assertThat(segunda.getEstado()).isEqualTo(EstadoEntrada.EN_REINTEGRO);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REINTEGRADO);
        verify(paymentGateway).reintegrar(pago.getId());
    }

    @Test
    void cancelarEventoSinEntradasDevuelveCero() {
        UUID eventoId = UUID.randomUUID();
        when(entradaRepository.findByEventoIdAndEstado(eventoId, EstadoEntrada.VALIDA)).thenReturn(List.of());
        when(entradaRepository.findByEventoIdAndEstado(eventoId, EstadoEntrada.EN_REINTEGRO)).thenReturn(List.of());

        var response = orderService.cancelarEvento(eventoId);

        assertThat(response.entradasAfectadas()).isZero();
        assertThat(response.reintegrosSolicitados()).isZero();
        verify(paymentGateway, never()).reintegrar(any());
    }

    private Entrada entrada(ReservationDto reserva, UUID qr) {
        Entrada entrada = new Entrada();
        entrada.setId(UUID.randomUUID());
        entrada.setQrCode(qr);
        entrada.setEstado(EstadoEntrada.VALIDA);
        entrada.setSectorId(reserva.items().getFirst().sectorId());
        entrada.setAsientoId(reserva.items().getFirst().asientoId());
        return entrada;
    }

    private Entrada entradaCancelacion(UUID eventoId, UUID ordenId) {
        Entrada entrada = new Entrada();
        entrada.setId(UUID.randomUUID());
        entrada.setOrdenId(ordenId);
        entrada.setClienteId(clienteId);
        entrada.setEventoId(eventoId);
        entrada.setEventoNombre("Recital");
        entrada.setEventoFecha(OffsetDateTime.parse("2027-01-15T21:00:00-03:00"));
        entrada.setSectorId(UUID.randomUUID());
        entrada.setAsientoId(UUID.randomUUID());
        entrada.setQrCode(UUID.randomUUID());
        entrada.setEstado(EstadoEntrada.VALIDA);
        return entrada;
    }

    private ReservationDto reserva(String estado, OffsetDateTime expiresAt) {
        UUID eventoId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        return new ReservationDto(
                UUID.randomUUID(),
                eventoId,
                "Recital",
                OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                clienteId,
                estado,
                expiresAt,
                List.of(
                        new ReservationDto.Item(sectorId, UUID.randomUUID(), 1,
                                new BigDecimal("15000.00"), new BigDecimal("15000.00")),
                        new ReservationDto.Item(sectorId, UUID.randomUUID(), 1,
                                new BigDecimal("15000.00"), new BigDecimal("15000.00"))),
                new BigDecimal("30000.00"));
    }
}
