package ar.edu.unicen.ticketya.inventory.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.inventory.dominio.AsientoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoAsiento;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoEventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.EstadoReserva;
import ar.edu.unicen.ticketya.inventory.dominio.EventoInventario;
import ar.edu.unicen.ticketya.inventory.dominio.Reserva;
import ar.edu.unicen.ticketya.inventory.dominio.ReservaItem;
import ar.edu.unicen.ticketya.inventory.dominio.SectorInventario;
import ar.edu.unicen.ticketya.inventory.dominio.TipoSectorInventario;
import ar.edu.unicen.ticketya.inventory.repositorio.AsientoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.EventoInventarioRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaItemRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.ReservaRepository;
import ar.edu.unicen.ticketya.inventory.repositorio.SectorInventarioRepository;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
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
    private JdbcTemplate jdbcTemplate;
    @Mock
    private LimitePorCliente limitePorCliente;
    @Mock
    private DisponibilidadSincronizador disponibilidadSincronizador;
    private VencimientoService vencimientoService;

    private ReservaService reservaService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZONA);

    @BeforeEach
    void setUp() {
        reservaService = new ReservaService(eventoRepository, sectorRepository, asientoRepository,
                reservaRepository, reservaItemRepository, jdbcTemplate, limitePorCliente,
                vencimientoService, disponibilidadSincronizador, clock);
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

        UUID clienteId = UUID.randomUUID();
        ReservationResponse response = reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(), List.of(asiento.getAsientoId()), null))),
                clienteId);

        assertThat(response.estado()).isEqualTo(EstadoReserva.VIGENTE);
        verify(limitePorCliente).verificar(evento.getEventoId(), clienteId, 4, 1);
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
    void superarElLimiteNoReservaNada() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        UUID clienteId = UUID.randomUUID();
        UUID asientoId = UUID.randomUUID();
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEventoIdAndSectorId(evento.getEventoId(), sector.getSectorId()))
                .thenReturn(Optional.of(sector));
        doThrow(ApiException.unprocessable("CLIENT_LIMIT_EXCEEDED", "supera el límite"))
                .when(limitePorCliente).verificar(evento.getEventoId(), clienteId, 4, 1);

        assertThatThrownBy(() -> reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(sector.getSectorId(), List.of(asientoId), null))),
                clienteId))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                    assertThat(ex.getCode()).isEqualTo("CLIENT_LIMIT_EXCEEDED");
                });
        verify(reservaRepository, never()).save(any());
        verify(asientoRepository, never()).bloquearAsientos(any(), anyCollection());
    }

    @Test
    void unEventoConFechaPasadaNoSeReserva() {
        EventoInventario evento = evento();
        evento.setFecha(OffsetDateTime.parse("2026-11-01T21:00:00-03:00"));
        when(eventoRepository.findById(evento.getEventoId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> reservaService.reservar(
                new ReservaRequest(evento.getEventoId(), List.of(
                        new ReservaRequest.Item(UUID.randomUUID(), List.of(UUID.randomUUID()), null))),
                UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_FINISHED");
                });
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void unAsientoDeOtroSectorNoSeReserva() {
        EventoInventario evento = evento();
        SectorInventario sector = sector(evento.getEventoId(), TipoSectorInventario.NUMERADO);
        AsientoInventario asiento = asiento(evento.getEventoId(), UUID.randomUUID(), EstadoAsiento.LIBRE);
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
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("SECTOR_MISMATCH");
                });
        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.LIBRE);
        verify(asientoRepository, never()).saveAll(any());
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

    private EventoInventario evento() {
        EventoInventario evento = new EventoInventario();
        evento.setEventoId(UUID.randomUUID());
        evento.setNombre("Recital");
        evento.setFecha(OffsetDateTime.parse("2027-01-15T21:00:00-03:00"));
        evento.setEstado(EstadoEventoInventario.PUBLICADO);
        evento.setLimitePorCliente(4);
        return evento;
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
