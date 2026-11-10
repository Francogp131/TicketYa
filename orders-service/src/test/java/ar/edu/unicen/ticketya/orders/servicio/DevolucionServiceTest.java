package ar.edu.unicen.ticketya.orders.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DevolucionServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZONA);

    @Mock
    private DevolucionRepository devolucionRepository;
    @Mock
    private EntradaRepository entradaRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private OrdenItemRepository ordenItemRepository;
    @Mock
    private ReintegroRepository reintegroRepository;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private PaymentGateway paymentGateway;

    private DevolucionService service;
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DevolucionService(devolucionRepository, entradaRepository,
                pagoRepository, ordenItemRepository, reintegroRepository,
                inventoryClient, paymentGateway, CLOCK);
    }

    @Test
    void solicitarDentroDelPlazoCreaUnaDevolucionPendiente() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));
        when(devolucionRepository.existsByEntradaIdAndEstadoIn(any(), any())).thenReturn(false);
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = service.solicitar(entrada.getId(), clienteId, "  No puedo asistir  ");

        assertThat(response.estado()).isEqualTo(EstadoDevolucion.PENDIENTE);
        assertThat(response.motivo()).isEqualTo("No puedo asistir");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.resolvedAt()).isNull();
    }

    @Test
    void solicitarFueraDelPlazoDevuelve422() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-09T10:00:00-03:00"));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));

        assertThatThrownBy(() -> service.solicitar(entrada.getId(), clienteId, "tarde"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                    assertThat(ex.getCode()).isEqualTo("RETURN_WINDOW_CLOSED");
                });
    }

    @Test
    void solicitarUnaEntradaAjenaDevuelve403() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));

        assertThatThrownBy(() -> service.solicitar(entrada.getId(), UUID.randomUUID(), "motivo"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("NOT_TICKET_OWNER");
                });
    }

    @Test
    void solicitarUnaEntradaYaDevueltaDevuelve409() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        entrada.setEstado(EstadoEntrada.DEVUELTA);
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));

        assertThatThrownBy(() -> service.solicitar(entrada.getId(), clienteId, "motivo"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("TICKET_NOT_VALID");
                });
        verify(devolucionRepository, never()).save(any());
    }

    @Test
    void aprobarLiberaLaEntradaYReintegraElPago() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.PENDIENTE);
        Pago pago = pago(entrada.getOrdenId(), EstadoPago.APROBADO, new BigDecimal("15000.00"));
        OrdenItem item = ordenItem(entrada.getOrdenItemId(), entrada.getOrdenId(), new BigDecimal("15000.00"));
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));
        when(pagoRepository.findByOrdenId(entrada.getOrdenId())).thenReturn(List.of(pago));
        when(ordenItemRepository.findById(entrada.getOrdenItemId())).thenReturn(Optional.of(item));
        when(entradaRepository.save(any(Entrada.class))).thenAnswer(inv -> inv.getArgument(0));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        List<Reintegro> reintegros = new ArrayList<>();
        when(reintegroRepository.findByPagoId(pago.getId())).thenAnswer(inv -> new ArrayList<>(reintegros));
        when(reintegroRepository.save(any(Reintegro.class))).thenAnswer(inv -> {
            Reintegro r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            reintegros.removeIf(x -> x.getId() != null && x.getId().equals(r.getId()));
            reintegros.add(r);
            return r;
        });

        ReturnResponse response = service.aprobar(devolucion.getId());

        assertThat(response.estado()).isEqualTo(EstadoDevolucion.APROBADA);
        assertThat(entrada.getEstado()).isEqualTo(EstadoEntrada.DEVUELTA);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REINTEGRADO);
        ArgumentCaptor<ReturnDto> returnDtoCaptor = ArgumentCaptor.forClass(ReturnDto.class);
        verify(inventoryClient).devolverItems(returnDtoCaptor.capture());
        assertThat(returnDtoCaptor.getValue().clienteId()).isEqualTo(entrada.getClienteId());
        verify(paymentGateway).reintegrar(pago.getId(), new BigDecimal("15000.00"));
    }

    @Test
    void siInventoryFallaLaDevolucionQuedaProcesando() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.PENDIENTE);
        Pago pago = pago(entrada.getOrdenId(), EstadoPago.APROBADO, new BigDecimal("15000.00"));
        OrdenItem item = ordenItem(entrada.getOrdenItemId(), entrada.getOrdenId(), new BigDecimal("15000.00"));
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.findByOrdenId(entrada.getOrdenId())).thenReturn(List.of(pago));
        when(ordenItemRepository.findById(entrada.getOrdenItemId())).thenReturn(Optional.of(item));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        List<Reintegro> reintegros = new ArrayList<>();
        when(reintegroRepository.findByPagoId(pago.getId())).thenAnswer(inv -> new ArrayList<>(reintegros));
        when(reintegroRepository.save(any(Reintegro.class))).thenAnswer(inv -> {
            Reintegro r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            reintegros.removeIf(x -> x.getId() != null && x.getId().equals(r.getId()));
            reintegros.add(r);
            return r;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("inventory caído"))
                .when(inventoryClient).devolverItems(any());

        assertThatThrownBy(() -> service.aprobar(devolucion.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(502);
                    assertThat(ex.getCode()).isEqualTo("INVENTORY_UNAVAILABLE");
                });

        ArgumentCaptor<Devolucion> captor = ArgumentCaptor.forClass(Devolucion.class);
        verify(devolucionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getEstado())
                .isEqualTo(EstadoDevolucion.PROCESANDO);
        assertThat(entrada.getEstado()).isEqualTo(EstadoEntrada.EN_DEVOLUCION);
        verify(paymentGateway).reintegrar(pago.getId(), new BigDecimal("15000.00"));
    }

    @Test
    void noSePuedeRechazarUnaDevolucionEnProceso() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.PROCESANDO);
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));

        assertThatThrownBy(() -> service.rechazar(devolucion.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("RETURN_IN_PROGRESS");
                });
        assertThat(devolucion.getEstado()).isEqualTo(EstadoDevolucion.PROCESANDO);
        verify(devolucionRepository, never()).save(any());
    }

    @Test
    void rechazarMantieneLaEntradaValida() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.PENDIENTE);
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = service.rechazar(devolucion.getId());

        assertThat(response.estado()).isEqualTo(EstadoDevolucion.RECHAZADA);
        assertThat(entrada.getEstado()).isEqualTo(EstadoEntrada.VALIDA);
        verify(entradaRepository, never()).save(any());
    }

    @Test
    void noSePuedeResolverDosVeces() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.APROBADA);
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));

        assertThatThrownBy(() -> service.aprobar(devolucion.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("RETURN_ALREADY_RESOLVED");
                });
    }

    @Test
    void devolverUnaDeCuatroReintegraSoloSuPrecio() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.PENDIENTE);
        Pago pago = pago(entrada.getOrdenId(), EstadoPago.APROBADO, new BigDecimal("60000.00"));
        OrdenItem item = ordenItem(entrada.getOrdenItemId(), entrada.getOrdenId(), new BigDecimal("15000.00"));
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));
        when(pagoRepository.findByOrdenId(entrada.getOrdenId())).thenReturn(List.of(pago));
        when(ordenItemRepository.findById(entrada.getOrdenItemId())).thenReturn(Optional.of(item));
        when(entradaRepository.save(any(Entrada.class))).thenAnswer(inv -> inv.getArgument(0));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        List<Reintegro> reintegros = new ArrayList<>();
        when(reintegroRepository.findByPagoId(pago.getId())).thenAnswer(inv -> new ArrayList<>(reintegros));
        when(reintegroRepository.save(any(Reintegro.class))).thenAnswer(inv -> {
            Reintegro r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            reintegros.removeIf(x -> x.getId() != null && x.getId().equals(r.getId()));
            reintegros.add(r);
            return r;
        });

        service.aprobar(devolucion.getId());

        verify(paymentGateway).reintegrar(pago.getId(), new BigDecimal("15000.00"));
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REINTEGRO_PENDIENTE);
    }

    @Test
    void devolverLasCuatroReintegraElTotalYDejaPagoReintegrado() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.PENDIENTE);
        Pago pago = pago(entrada.getOrdenId(), EstadoPago.REINTEGRO_PENDIENTE, new BigDecimal("60000.00"));
        OrdenItem item = ordenItem(entrada.getOrdenItemId(), entrada.getOrdenId(), new BigDecimal("15000.00"));
        // 3 reintegros previos ya aprobados por 15000 c/u
        List<Reintegro> reintegros = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Reintegro prev = new Reintegro(pago.getId(), UUID.randomUUID(), new BigDecimal("15000.00"), EstadoReintegro.APROBADO);
            prev.setId(UUID.randomUUID());
            reintegros.add(prev);
        }
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));
        when(pagoRepository.findByOrdenId(entrada.getOrdenId())).thenReturn(List.of(pago));
        when(ordenItemRepository.findById(entrada.getOrdenItemId())).thenReturn(Optional.of(item));
        when(entradaRepository.save(any(Entrada.class))).thenAnswer(inv -> inv.getArgument(0));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reintegroRepository.findByPagoId(pago.getId())).thenAnswer(inv -> new ArrayList<>(reintegros));
        when(reintegroRepository.save(any(Reintegro.class))).thenAnswer(inv -> {
            Reintegro r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            reintegros.removeIf(x -> x.getId() != null && x.getId().equals(r.getId()));
            reintegros.add(r);
            return r;
        });

        service.aprobar(devolucion.getId());

        verify(paymentGateway).reintegrar(pago.getId(), new BigDecimal("15000.00"));
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REINTEGRADO);
    }

    @Test
    void siLaPasarelaFallaElReintegroQuedaPendienteYElReintentoLoAprueba() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.PENDIENTE);
        Pago pago = pago(entrada.getOrdenId(), EstadoPago.APROBADO, new BigDecimal("15000.00"));
        OrdenItem item = ordenItem(entrada.getOrdenItemId(), entrada.getOrdenId(), new BigDecimal("15000.00"));
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));
        when(pagoRepository.findByOrdenId(entrada.getOrdenId())).thenReturn(List.of(pago));
        when(ordenItemRepository.findById(entrada.getOrdenItemId())).thenReturn(Optional.of(item));
        when(entradaRepository.save(any(Entrada.class))).thenAnswer(inv -> inv.getArgument(0));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        List<Reintegro> reintegros = new ArrayList<>();
        when(reintegroRepository.findByPagoId(pago.getId())).thenAnswer(inv -> new ArrayList<>(reintegros));
        when(reintegroRepository.save(any(Reintegro.class))).thenAnswer(inv -> {
            Reintegro r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            reintegros.removeIf(x -> x.getId() != null && x.getId().equals(r.getId()));
            reintegros.add(r);
            return r;
        });

        org.mockito.Mockito.doThrow(new RuntimeException("pasarela caída"))
                .when(paymentGateway).reintegrar(any(), any());

        assertThatThrownBy(() -> service.aprobar(devolucion.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(502);
                    assertThat(ex.getCode()).isEqualTo("PAYMENT_GATEWAY_ERROR");
                });
        assertThat(devolucion.getEstado()).isEqualTo(EstadoDevolucion.PROCESANDO);
        assertThat(reintegros).hasSize(1);
        assertThat(reintegros.get(0).getEstado()).isEqualTo(EstadoReintegro.PENDIENTE);

        // reintento: la pasarela responde OK
        org.mockito.Mockito.doNothing().when(paymentGateway).reintegrar(any(), any());

        ReturnResponse response = service.aprobar(devolucion.getId());

        assertThat(response.estado()).isEqualTo(EstadoDevolucion.APROBADA);
        assertThat(entrada.getEstado()).isEqualTo(EstadoEntrada.DEVUELTA);
        assertThat(reintegros.get(0).getEstado()).isEqualTo(EstadoReintegro.APROBADO);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REINTEGRADO);
    }

    private Entrada entrada(OffsetDateTime eventoFecha) {
        Entrada entrada = new Entrada();
        entrada.setId(UUID.randomUUID());
        entrada.setClienteId(clienteId);
        entrada.setOrdenId(UUID.randomUUID());
        entrada.setOrdenItemId(UUID.randomUUID());
        entrada.setEventoId(UUID.randomUUID());
        entrada.setEventoNombre("Festival");
        entrada.setEventoFecha(eventoFecha);
        entrada.setSectorId(UUID.randomUUID());
        entrada.setAsientoId(UUID.randomUUID());
        entrada.setQrCode(UUID.randomUUID());
        entrada.setEstado(EstadoEntrada.VALIDA);
        return entrada;
    }

    private Devolucion devolucion(UUID entradaId, EstadoDevolucion estado) {
        Devolucion devolucion = new Devolucion();
        devolucion.setId(UUID.randomUUID());
        devolucion.setEntradaId(entradaId);
        devolucion.setEstado(estado);
        return devolucion;
    }

    private Pago pago(UUID ordenId, EstadoPago estado) {
        return pago(ordenId, estado, new BigDecimal("15000.00"));
    }

    private Pago pago(UUID ordenId, EstadoPago estado, BigDecimal monto) {
        Pago pago = new Pago();
        pago.setId(UUID.randomUUID());
        pago.setOrdenId(ordenId);
        pago.setEstado(estado);
        pago.setMonto(monto);
        return pago;
    }

    private OrdenItem ordenItem(UUID id, UUID ordenId, BigDecimal precioUnitario) {
        OrdenItem item = new OrdenItem();
        item.setId(id);
        item.setOrdenId(ordenId);
        item.setPrecioUnitario(precioUnitario);
        item.setCantidad(1);
        item.setSubtotal(precioUnitario);
        return item;
    }
}
