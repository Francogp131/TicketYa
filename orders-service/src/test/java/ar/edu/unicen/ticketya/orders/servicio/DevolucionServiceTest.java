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
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.repositorio.DevolucionRepository;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.ReturnResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
    private InventoryClient inventoryClient;
    @Mock
    private PaymentGateway paymentGateway;

    private DevolucionService service;
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DevolucionService(devolucionRepository, entradaRepository,
                pagoRepository, inventoryClient, paymentGateway, CLOCK);
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
        Pago pago = pago(entrada.getOrdenId(), EstadoPago.APROBADO);
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));
        when(pagoRepository.findByOrdenId(entrada.getOrdenId())).thenReturn(List.of(pago));
        when(entradaRepository.save(any(Entrada.class))).thenAnswer(inv -> inv.getArgument(0));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = service.aprobar(devolucion.getId());

        assertThat(response.estado()).isEqualTo(EstadoDevolucion.APROBADA);
        assertThat(entrada.getEstado()).isEqualTo(EstadoEntrada.DEVUELTA);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REINTEGRADO);
        verify(inventoryClient).devolverItems(any(ReturnDto.class));
        verify(paymentGateway).reintegrar(pago.getId());
    }

    @Test
    void siInventoryFallaLaDevolucionQuedaProcesando() {
        Entrada entrada = entrada(OffsetDateTime.parse("2026-11-10T21:00:00-03:00"));
        Devolucion devolucion = devolucion(entrada.getId(), EstadoDevolucion.PENDIENTE);
        when(devolucionRepository.findById(devolucion.getId())).thenReturn(Optional.of(devolucion));
        when(entradaRepository.findById(entrada.getId())).thenReturn(Optional.of(entrada));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> inv.getArgument(0));
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
        assertThat(entrada.getEstado()).isEqualTo(EstadoEntrada.VALIDA);
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

    private Entrada entrada(OffsetDateTime eventoFecha) {
        Entrada entrada = new Entrada();
        entrada.setId(UUID.randomUUID());
        entrada.setClienteId(clienteId);
        entrada.setOrdenId(UUID.randomUUID());
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
        Pago pago = new Pago();
        pago.setId(UUID.randomUUID());
        pago.setOrdenId(ordenId);
        pago.setEstado(estado);
        return pago;
    }
}
