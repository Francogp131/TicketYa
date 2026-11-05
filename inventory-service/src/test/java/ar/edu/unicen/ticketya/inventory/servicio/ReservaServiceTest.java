package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.inventory.dominio.AsientoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.ClienteEvento;
import ar.edu.unicen.ticketya.inventory.dominio.ClienteEventoId;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoAsiento;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.Reserva;
import ar.edu.unicen.ticketya.inventory.dominio.ReservaItem;
import ar.edu.unicen.ticketya.inventory.dominio.SectorInventario;
import ar.edu.unicen.ticketya.inventory.dominio.TipoSectorInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.AsientoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ClienteEventoRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaItemRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.SectorInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReturnRequest;
import java.math.BigDecimal;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private EventoInventarioRepository eventoRepository;
    @Mock
    private SectorInventarioRepository sectorRepository;
    @Mock
    private AsientoInventarioRepository asientoRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private ReservaItemRepository reservaItemRepository;
    @Mock
    private ClienteEventoRepository clienteEventoRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private ReservaService reservaService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZONA);

    @BeforeEach
    void setUp() {
        reservaService = new ReservaService(eventoRepository, sectorRepository, asientoRepository,
                reservaRepository, reservaItemRepository, clienteEventoRepository, jdbcTemplate, clock);
    }

    @Test
    void reservarUnAsientoLibreDevuelve201YTotal() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        AsientoInventario asiento = asiento(evento.getEventoId(), sector.getSectorId(), EstadoAsiento.LIBRE);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdAndSectorId(evento.getEventoId(), sector.getSectorId()))
                .thenReturn(Optional.of(sector));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva reserva = inv.getArgument(0);
            reserva.setId(UUID.randomUUID());
            return reserva;
        });
        when(asientoRepository.bloquearAsientos(eq(evento.getEventoId()), anyCollection()))
                .thenReturn(List.of(asiento));

        ReservationResponse response = reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(), List.of(asiento.getAsientoId()), null))),
                UUID.randomUUID());

        assertThat(response.estado()).isEqualTo(EstadoReserva.VIGENTE);
        assertThat(response.total()).isEqualByComparingTo("15000.00");
        assertThat(response.items()).hasSize(1);
        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.RESERVADO);
        assertThat(asiento.getReservaId()).isEqualTo(response.id());
        verify(reservaItemRepository).saveAll(any());
    }

    @Test
    void reservarUnAsientoOcupadoNoReservaNada() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        AsientoInventario asiento = asiento(evento.getEventoId(), sector.getSectorId(), EstadoAsiento.VENDIDO);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdAndSectorId(evento.getEventoId(), sector.getSectorId()))
                .thenReturn(Optional.of(sector));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva reserva = inv.getArgument(0);
            reserva.setId(UUID.randomUUID());
            return reserva;
        });
        when(asientoRepository.bloquearAsientos(eq(evento.getEventoId()), anyCollection()))
                .thenReturn(List.of(asiento));

        assertThatThrownBy(() -> reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(), List.of(asiento.getAsientoId()), null))),
                UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("SEAT_NOT_AVAILABLE");
                });
        verify(asientoRepository, never()).saveAll(any());
    }

    @Test
    void siFaltaUnoDeVariosAsientosNoSeReservaNinguno() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        AsientoInventario libre = asiento(evento.getEventoId(), sector.getSectorId(), EstadoAsiento.LIBRE);
        UUID faltante = UUID.randomUUID();
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdAndSectorId(evento.getEventoId(), sector.getSectorId()))
                .thenReturn(Optional.of(sector));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva reserva = inv.getArgument(0);
            reserva.setId(UUID.randomUUID());
            return reserva;
        });
        // El lock devuelve menos asientos que los pedidos: uno ya no existe.
        when(asientoRepository.bloquearAsientos(eq(evento.getEventoId()), anyCollection()))
                .thenReturn(List.of(libre));

        assertThatThrownBy(() -> reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(),
                                List.of(libre.getAsientoId(), faltante), null))),
                UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("SEAT_NOT_AVAILABLE"));
        assertThat(libre.getEstado()).isEqualTo(EstadoAsiento.LIBRE);
    }

    @Test
    void unaZonaSinDisponibilidadDevuelveConflict() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NO_NUMERADO);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdAndSectorId(evento.getEventoId(), sector.getSectorId()))
                .thenReturn(Optional.of(sector));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva reserva = inv.getArgument(0);
            reserva.setId(UUID.randomUUID());
            return reserva;
        });
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any()))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(), null, 2))),
                UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_AVAILABILITY");
                });
    }

    @Test
    void unEventoCanceladoNoSePuedeReservar() {
        EventoInventario evento = evento();
        evento.setEstado(EstadoEventoInventario.CANCELADO);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(UUID.randomUUID(), List.of(UUID.randomUUID()), null))),
                UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("EVENT_NOT_ON_SALE"));
    }

    @Test
    void reservarUnaZonaConDisponibilidadSuficienteDescuentaLaCantidad() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NO_NUMERADO);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdAndSectorId(evento.getEventoId(), sector.getSectorId()))
                .thenReturn(Optional.of(sector));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva reserva = inv.getArgument(0);
            reserva.setId(UUID.randomUUID());
            return reserva;
        });
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any()))
                .thenReturn(8);

        ReservationResponse response = reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(), null, 2))),
                UUID.randomUUID());

        assertThat(response.total()).isEqualByComparingTo("30000.00");
        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.cantidad()).isEqualTo(2));
        verify(jdbcTemplate).queryForObject(anyString(), eq(Integer.class), eq(2),
                eq(evento.getEventoId()), eq(sector.getSectorId()), eq(2));
    }

    @Test
    void superarElLimitePorClienteDevuelve422SinCrearReserva() {
        EventoInventario evento = evento();
        evento.setLimitePorCliente(2);
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NO_NUMERADO);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdAndSectorId(evento.getEventoId(), sector.getSectorId()))
                .thenReturn(Optional.of(sector));
        when(reservaItemRepository.sumarCantidadVigente(any(), any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(), null, 2))),
                UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                    assertThat(ex.getCode()).isEqualTo("CLIENT_LIMIT_EXCEEDED");
                });
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void lasVentasPreviasTambienCuentanParaElLimite() {
        EventoInventario evento = evento();
        evento.setLimitePorCliente(2);
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NO_NUMERADO);
        ClienteEvento comprador = new ClienteEvento();
        comprador.setId(new ClienteEventoId(evento.getEventoId(), UUID.randomUUID()));
        comprador.setEntradasVendidas(2);
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdAndSectorId(evento.getEventoId(), sector.getSectorId()))
                .thenReturn(Optional.of(sector));
        when(clienteEventoRepository.findById(any())).thenReturn(Optional.of(comprador));

        assertThatThrownBy(() -> reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(), null, 1))),
                comprador.getId().getClienteId()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CLIENT_LIMIT_EXCEEDED"));
    }

    @Test
    void consultarUnaReservaVencidaLaLiberaYDevuelveEstadoVencida() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        Reserva reserva = reserva(evento, EstadoReserva.VIGENTE,
                OffsetDateTime.parse("2026-11-03T08:55:00-03:00"));
        AsientoInventario asiento = asiento(evento.getEventoId(), sector.getSectorId(), EstadoAsiento.RESERVADO);
        asiento.setReservaId(reserva.getId());
        ReservaItem item = new ReservaItem(reserva.getId(), sector.getSectorId(), asiento.getAsientoId(), 1);
        when(reservaRepository.findById(reserva.getId())).thenReturn(Optional.of(reserva));
        when(reservaItemRepository.findByReservaId(reserva.getId())).thenReturn(List.of(item));
        when(asientoRepository.bloquearAsientos(eq(evento.getEventoId()), anyCollection()))
                .thenReturn(List.of(asiento));
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdOrderByNombreAsc(evento.getEventoId()))
                .thenReturn(List.of(sector));

        ReservationResponse response = reservaService.obtener(reserva.getId(), reserva.getClienteId());

        assertThat(response.estado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.LIBRE);
        verify(reservaRepository).save(reserva);
    }

    @Test
    void elJobLiberaLasReservasVencidasYCuentaCuantasProceso() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        Reserva reserva = reserva(evento, EstadoReserva.VIGENTE,
                OffsetDateTime.parse("2026-11-03T08:55:00-03:00"));
        AsientoInventario asiento = asiento(evento.getEventoId(), sector.getSectorId(), EstadoAsiento.RESERVADO);
        asiento.setReservaId(reserva.getId());
        ReservaItem item = new ReservaItem(reserva.getId(), sector.getSectorId(), asiento.getAsientoId(), 1);
        when(reservaRepository.findByEstadoAndExpiresAtBefore(eq(EstadoReserva.VIGENTE), any()))
                .thenReturn(List.of(reserva));
        when(reservaItemRepository.findByReservaId(reserva.getId())).thenReturn(List.of(item));
        when(asientoRepository.bloquearAsientos(eq(evento.getEventoId()), anyCollection()))
                .thenReturn(List.of(asiento));

        int liberadas = reservaService.liberarVencidas();

        assertThat(liberadas).isEqualTo(1);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.LIBRE);
    }

    @Test
    void confirmarUnaReservaVigenteVendeElAsientoYRegistraLaEntrada() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        Reserva reserva = reserva(evento, EstadoReserva.VIGENTE,
                OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        AsientoInventario asiento = asiento(evento.getEventoId(), sector.getSectorId(), EstadoAsiento.RESERVADO);
        asiento.setReservaId(reserva.getId());
        ReservaItem item = new ReservaItem(reserva.getId(), sector.getSectorId(), asiento.getAsientoId(), 1);
        when(reservaRepository.findById(reserva.getId())).thenReturn(Optional.of(reserva));
        when(reservaItemRepository.findByReservaId(reserva.getId())).thenReturn(List.of(item));
        when(asientoRepository.bloquearAsientos(eq(evento.getEventoId()), anyCollection()))
                .thenReturn(List.of(asiento));
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdOrderByNombreAsc(evento.getEventoId()))
                .thenReturn(List.of(sector));

        ReservationResponse response = reservaService.confirmar(reserva.getId());

        assertThat(response.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.VENDIDO);
        verify(clienteEventoRepository).save(any(ClienteEvento.class));
    }

    @Test
    void confirmarDosVecesEsIdempotenteYNoVuelveASumarLaVenta() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        Reserva reserva = reserva(evento, EstadoReserva.CONFIRMADA,
                OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        when(reservaRepository.findById(reserva.getId())).thenReturn(Optional.of(reserva));
        when(reservaItemRepository.findByReservaId(reserva.getId())).thenReturn(List.of());
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdOrderByNombreAsc(evento.getEventoId()))
                .thenReturn(List.of(sector));

        ReservationResponse response = reservaService.confirmar(reserva.getId());

        assertThat(response.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(clienteEventoRepository, never()).save(any());
        verify(asientoRepository, never()).bloquearAsientos(any(), anyCollection());
    }

    @Test
    void confirmarUnaReservaVencidaDevuelve410YLibera() {
        EventoInventario evento = evento();
        Reserva reserva = reserva(evento, EstadoReserva.VIGENTE,
                OffsetDateTime.parse("2026-11-03T08:55:00-03:00"));
        when(reservaRepository.findById(reserva.getId())).thenReturn(Optional.of(reserva));
        when(reservaItemRepository.findByReservaId(reserva.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> reservaService.confirmar(reserva.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(410);
                    assertThat(ex.getCode()).isEqualTo("RESERVATION_EXPIRED");
                });
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.VENCIDA);
    }

    @Test
    void liberarDosVecesEsIdempotente() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        Reserva reserva = reserva(evento, EstadoReserva.VIGENTE,
                OffsetDateTime.parse("2026-11-03T12:10:00-03:00"));
        AsientoInventario asiento = asiento(evento.getEventoId(), sector.getSectorId(), EstadoAsiento.RESERVADO);
        asiento.setReservaId(reserva.getId());
        ReservaItem item = new ReservaItem(reserva.getId(), sector.getSectorId(), asiento.getAsientoId(), 1);
        when(reservaRepository.findById(reserva.getId())).thenReturn(Optional.of(reserva));
        when(reservaItemRepository.findByReservaId(reserva.getId())).thenReturn(List.of(item));
        when(asientoRepository.bloquearAsientos(eq(evento.getEventoId()), anyCollection()))
                .thenReturn(List.of(asiento));

        reservaService.liberar(reserva.getId(), "PAGO_RECHAZADO");
        reservaService.liberar(reserva.getId(), "PAGO_RECHAZADO");

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.LIBERADA);
        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.LIBRE);
        verify(asientoRepository, org.mockito.Mockito.times(1))
                .bloquearAsientos(eq(evento.getEventoId()), anyCollection());
    }

    @Test
    void devolverUnAsientoVendidoLoLibera() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        AsientoInventario asiento = asiento(evento.getEventoId(), sector.getSectorId(), EstadoAsiento.VENDIDO);
        UUID referencia = UUID.randomUUID();
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(asientoRepository.bloquearAsientos(eq(evento.getEventoId()), anyCollection()))
                .thenReturn(List.of(asiento));

        reservaService.devolver(new ReturnRequest(evento.getEventoId(), referencia, List.of(
                new ReturnRequest.Item(sector.getSectorId(), asiento.getAsientoId(), null))));

        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.LIBRE);
        verify(jdbcTemplate).update(anyString(), eq(referencia), eq(evento.getEventoId()));
    }

    @Test
    void unaDevolucionYaProcesadaNoVuelveAAplicarse() {
        EventoInventario evento = evento();
        UUID referencia = UUID.randomUUID();
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        reservaService.devolver(new ReturnRequest(evento.getEventoId(), referencia, List.of(
                new ReturnRequest.Item(UUID.randomUUID(), null, 1))));

        verify(asientoRepository, never()).bloquearAsientos(any(), anyCollection());
    }

    private EventoInventario evento() {
        EventoInventario evento = new EventoInventario();
        evento.setEventoId(UUID.randomUUID());
        evento.setNombre("Recital");
        evento.setFecha(OffsetDateTime.parse("2027-01-15T21:00:00-03:00"));
        evento.setEstado(EstadoEventoInventario.PUBLICADO);
        evento.setLimitePorCliente(4);
        return evento;
    }

    private Reserva reserva(EventoInventario evento, EstadoReserva estado, OffsetDateTime expiresAt) {
        Reserva reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setEventoId(evento.getEventoId());
        reserva.setClienteId(UUID.randomUUID());
        reserva.setEstado(estado);
        reserva.setExpiresAt(expiresAt);
        return reserva;
    }

    private SectorInventario sector(UUID eventoId, TipoSectorInventario tipo) {
        SectorInventario sector = new SectorInventario();
        sector.setId(UUID.randomUUID());
        sector.setEventoId(eventoId);
        sector.setSectorId(UUID.randomUUID());
        sector.setNombre("Platea");
        sector.setTipo(tipo);
        sector.setPrecio(new BigDecimal("15000.00"));
        sector.setCapacidad(10);
        sector.setDisponibles(tipo == TipoSectorInventario.NO_NUMERADO ? 10 : null);
        return sector;
    }

    private AsientoInventario asiento(UUID eventoId, UUID sectorId, EstadoAsiento estado) {
        AsientoInventario asiento = new AsientoInventario();
        asiento.setId(UUID.randomUUID());
        asiento.setEventoId(eventoId);
        asiento.setSectorId(sectorId);
        asiento.setAsientoId(UUID.randomUUID());
        asiento.setFila(1);
        asiento.setNumero(1);
        asiento.setEstado(estado);
        if (estado != EstadoAsiento.LIBRE) {
            asiento.setReservaId(UUID.randomUUID());
            asiento.setExpiresAt(OffsetDateTime.now());
        }
        return asiento;
    }
}
