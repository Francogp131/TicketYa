package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.catalog.dominio.EstadoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.Evento;
import ar.edu.unicen.ticketya.catalog.dominio.EventoSectorPrecio;
import ar.edu.unicen.ticketya.catalog.dominio.Lugar;
import ar.edu.unicen.ticketya.catalog.dominio.Sector;
import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import ar.edu.unicen.ticketya.catalog.repositorio.AsientoRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.EventoParticipanteRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.EventoRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.EventoSectorPrecioRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.LugarRepository;
import ar.edu.unicen.ticketya.catalog.repositorio.SectorRepository;
import ar.edu.unicen.ticketya.catalog.web.ApiException;
import ar.edu.unicen.ticketya.catalog.web.dto.CancelacionResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.CrearEventoRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoBusquedaResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PageResponse;
import ar.edu.unicen.ticketya.catalog.web.dto.PrecioRequest;
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

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private LugarRepository lugarRepository;
    @Mock
    private SectorRepository sectorRepository;
    @Mock
    private AsientoRepository asientoRepository;
    @Mock
    private EventoParticipanteRepository participanteRepository;
    @Mock
    private EventoSectorPrecioRepository precioRepository;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private OrdersClient ordersClient;

    private EventService eventService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZONA);

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventoRepository, lugarRepository, sectorRepository,
                asientoRepository, participanteRepository, precioRepository, inventoryClient, ordersClient, clock);
    }

    @Test
    void unEventoConFechaPasadaEsInvalido() {
        CrearEventoRequest request = new CrearEventoRequest("Viejo", null, TipoEvento.RECITAL,
                UUID.randomUUID(), OffsetDateTime.parse("2020-01-01T20:00:00-03:00"),
                List.of("Artista"), 4);

        assertThatThrownBy(() -> eventService.crearEvento(request, UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void publicarSinPreciosDevuelveEventoIncompleto() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByLugarIdOrderByNombreAsc(evento.getLugarId()))
                .thenReturn(List.of(sector(evento.getLugarId(), "Platea")));
        when(precioRepository.findByEventoId(evento.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> eventService.publicar(evento.getId(), evento.getOrganizadorId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_INCOMPLETE");
                });
        verify(inventoryClient, never()).publicarInventario(any());
    }

    @Test
    void publicarConInventarioOkDejaElEventoPublicado() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        Sector sector = sector(evento.getLugarId(), "Platea");
        EventoSectorPrecio precio = new EventoSectorPrecio(evento.getId(), sector.getId(), new BigDecimal("15000"));
        Lugar lugar = new Lugar();
        lugar.setId(evento.getLugarId());
        lugar.setNombre("Estadio");
        lugar.setCiudad("Tandil");

        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByLugarIdOrderByNombreAsc(evento.getLugarId())).thenReturn(List.of(sector));
        when(precioRepository.findByEventoId(evento.getId())).thenReturn(List.of(precio));
        when(asientoRepository.findBySectorIdOrderByFilaAscNumeroAsc(sector.getId())).thenReturn(List.of());
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lugarRepository.findById(evento.getLugarId())).thenReturn(Optional.of(lugar));
        when(participanteRepository.findByEventoIdOrderByNombreAsc(evento.getId())).thenReturn(List.of());

        EventoResponse response = eventService.publicar(evento.getId(), evento.getOrganizadorId());

        assertThat(response.estado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(inventoryClient).publicarInventario(any());
    }

    @Test
    void siInventoryFallaElEventoSigueEnBorrador() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        Sector sector = sector(evento.getLugarId(), "Platea");
        EventoSectorPrecio precio = new EventoSectorPrecio(evento.getId(), sector.getId(), new BigDecimal("15000"));
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByLugarIdOrderByNombreAsc(evento.getLugarId())).thenReturn(List.of(sector));
        when(precioRepository.findByEventoId(evento.getId())).thenReturn(List.of(precio));
        when(asientoRepository.findBySectorIdOrderByFilaAscNumeroAsc(sector.getId())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("inventory caído"))
                .when(inventoryClient).publicarInventario(any());

        assertThatThrownBy(() -> eventService.publicar(evento.getId(), evento.getOrganizadorId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(502);
                    assertThat(ex.getCode()).isEqualTo("INVENTORY_UNAVAILABLE");
                });
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.BORRADOR);
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void noSePuedeCambiarElPrecioDeOtroOrganizador() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.asignarPrecio(evento.getId(), UUID.randomUUID(),
                new PrecioRequest(new BigDecimal("100")), UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("NOT_EVENT_OWNER");
                });
    }

    @Test
    void unPrecioNegativoDevuelveInvalidPrice() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.asignarPrecio(evento.getId(), UUID.randomUUID(),
                new PrecioRequest(new BigDecimal("-1")), evento.getOrganizadorId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("INVALID_PRICE");
                });
    }

    @Test
    void unLimitePorClienteMenorAUnoEsInvalido() {
        CrearEventoRequest request = new CrearEventoRequest("Recital", null, TipoEvento.RECITAL,
                UUID.randomUUID(), OffsetDateTime.parse("2027-01-15T21:00:00-03:00"),
                List.of("Artista"), 0);

        assertThatThrownBy(() -> eventService.crearEvento(request, UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
                });
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void unEventoValidoQuedaEnBorradorConElOrganizadorDelHeader() {
        UUID organizadorId = UUID.randomUUID();
        UUID lugarId = UUID.randomUUID();
        Lugar lugar = new Lugar();
        lugar.setId(lugarId);
        lugar.setNombre("Estadio");
        lugar.setCiudad("Tandil");
        when(lugarRepository.findById(lugarId)).thenReturn(Optional.of(lugar));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> {
            Evento evento = invocation.getArgument(0);
            evento.setId(UUID.randomUUID());
            return evento;
        });

        CrearEventoRequest request = new CrearEventoRequest("Recital", null, TipoEvento.RECITAL,
                lugarId, OffsetDateTime.parse("2027-01-15T21:00:00-03:00"), List.of("Artista"), 4);

        EventoResponse response = eventService.crearEvento(request, organizadorId);

        assertThat(response.estado()).isEqualTo(EstadoEvento.BORRADOR);
        assertThat(response.organizadorId()).isEqualTo(organizadorId);
        verify(participanteRepository).saveAll(anyList());
    }

    @Test
    void unSectorDeOtroLugarDevuelveSectorNotFromVenue() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        UUID sectorId = UUID.randomUUID();
        Sector sectorDeOtroLugar = sector(UUID.randomUUID(), "Platea");
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findById(sectorId)).thenReturn(Optional.of(sectorDeOtroLugar));

        assertThatThrownBy(() -> eventService.asignarPrecio(evento.getId(), sectorId,
                new PrecioRequest(new BigDecimal("100")), evento.getOrganizadorId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("SECTOR_NOT_FROM_VENUE");
                });
    }

    @Test
    void noSePuedeCambiarElPrecioDeUnEventoPublicado() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.asignarPrecio(evento.getId(), UUID.randomUUID(),
                new PrecioRequest(new BigDecimal("100")), evento.getOrganizadorId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_EDITABLE");
                });
    }

    @Test
    void publicarUnEventoYaPublicadoDevuelveEventNotEditable() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.publicar(evento.getId(), evento.getOrganizadorId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_EDITABLE");
                });
        verify(inventoryClient, never()).publicarInventario(any());
    }

    @Test
    void republicarElMismoEventoNoVuelveAPublicarInventario() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        Sector sector = sector(evento.getLugarId(), "Platea");
        EventoSectorPrecio precio = new EventoSectorPrecio(evento.getId(), sector.getId(), new BigDecimal("15000"));
        Lugar lugar = new Lugar();
        lugar.setId(evento.getLugarId());
        lugar.setNombre("Estadio");
        lugar.setCiudad("Tandil");

        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(sectorRepository.findByLugarIdOrderByNombreAsc(evento.getLugarId())).thenReturn(List.of(sector));
        when(precioRepository.findByEventoId(evento.getId())).thenReturn(List.of(precio));
        when(asientoRepository.findBySectorIdOrderByFilaAscNumeroAsc(sector.getId())).thenReturn(List.of());
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lugarRepository.findById(evento.getLugarId())).thenReturn(Optional.of(lugar));
        when(participanteRepository.findByEventoIdOrderByNombreAsc(evento.getId())).thenReturn(List.of());

        eventService.publicar(evento.getId(), evento.getOrganizadorId());

        assertThatThrownBy(() -> eventService.publicar(evento.getId(), evento.getOrganizadorId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_EDITABLE");
                });
        verify(inventoryClient, times(1)).publicarInventario(any());
    }

    @Test
    void laBusquedaOrdenaPorPrecioYDevuelveElTotalDePaginas() {
        Evento caro = evento(UUID.randomUUID(), UUID.randomUUID());
        caro.setEstado(EstadoEvento.PUBLICADO);
        Evento barato = evento(UUID.randomUUID(), UUID.randomUUID());
        barato.setEstado(EstadoEvento.PUBLICADO);
        Lugar lugarCaro = lugar(caro.getLugarId(), "Estadio");
        Lugar lugarBarato = lugar(barato.getLugarId(), "Teatro");
        UUID sectorCaro = UUID.randomUUID();
        UUID sectorBarato = UUID.randomUUID();
        when(eventoRepository.buscar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(caro, barato));
        when(lugarRepository.findAllById(any())).thenReturn(List.of(lugarCaro, lugarBarato));
        when(precioRepository.findByEventoIdIn(any())).thenReturn(List.of(
                new EventoSectorPrecio(caro.getId(), sectorCaro, new BigDecimal("20000")),
                new EventoSectorPrecio(barato.getId(), sectorBarato, new BigDecimal("8000"))));

        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                null, null, null, null, null, null, "PRECIO_ASC", 0, 1);

        assertThat(pagina.content()).singleElement()
                .satisfies(evento -> {
                    assertThat(evento.id()).isEqualTo(barato.getId());
                    assertThat(evento.precioDesde()).isEqualByComparingTo("8000");
                });
        assertThat(pagina.totalElements()).isEqualTo(2);
        assertThat(pagina.totalPages()).isEqualTo(2);
    }

    @Test
    void laRelevanciaPriorizaLosPublicadosSobreLosAgotados() {
        Evento agotado = evento(UUID.randomUUID(), UUID.randomUUID());
        agotado.setEstado(EstadoEvento.AGOTADO);
        agotado.setFecha(OffsetDateTime.parse("2027-01-01T21:00:00-03:00"));
        Evento publicado = evento(UUID.randomUUID(), UUID.randomUUID());
        publicado.setEstado(EstadoEvento.PUBLICADO);
        publicado.setFecha(OffsetDateTime.parse("2027-02-01T21:00:00-03:00"));
        when(eventoRepository.buscar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(agotado, publicado));
        when(lugarRepository.findAllById(any())).thenReturn(List.of());
        when(precioRepository.findByEventoIdIn(any())).thenReturn(List.of());

        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                null, null, null, null, null, null, "RELEVANCIA", 0, 20);

        assertThat(pagina.content()).extracting(EventoBusquedaResponse::id)
                .containsExactly(publicado.getId(), agotado.getId());
    }

    @Test
    void unRangoDeFechasInvalidoDevuelve400() {
        assertThatThrownBy(() -> eventService.buscar(null, null, null, null,
                "2027-02-01T00:00:00-03:00", "2027-01-01T00:00:00-03:00",
                "FECHA_ASC", 0, 20))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("INVALID_DATE_RANGE");
                });
    }

    @Test
    void unOrdenDesconocidoDevuelve400() {
        assertThatThrownBy(() -> eventService.buscar(null, null, null, null,
                null, null, "CARO_PRIMERO", 0, 20))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("INVALID_SORT");
                });
    }

    @Test
    void cancelarUnEventoPublicadoLoMarcaCanceladoYAvisaAOrders() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelacionResponse response = eventService.cancelar(evento.getId());

        assertThat(response.estado()).isEqualTo(EstadoEvento.CANCELADO);
        assertThat(response.cancelacionPendiente()).isFalse();
        verify(ordersClient).cancelarEvento(evento.getId());
    }

    @Test
    void siOrdersNoRespondeLaCancelacionQuedaPendiente() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("orders caído"))
                .when(ordersClient).cancelarEvento(evento.getId());

        CancelacionResponse response = eventService.cancelar(evento.getId());

        assertThat(response.estado()).isEqualTo(EstadoEvento.CANCELADO);
        assertThat(response.cancelacionPendiente()).isTrue();
    }

    @Test
    void noSePuedeCancelarUnEventoYaCancelado() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.CANCELADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.cancelar(evento.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_CANCELLABLE");
                });
    }

    @Test
    void elCallbackDeDisponibilidadEsIdempotente() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        eventService.actualizarDisponibilidad(evento.getId(), true);
        eventService.actualizarDisponibilidad(evento.getId(), true);

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.AGOTADO);
        verify(eventoRepository, times(1)).save(evento);
    }

    @Test
    void elCallbackPuedeVolverAAbilitarUnEventoAgotado() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.AGOTADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        eventService.actualizarDisponibilidad(evento.getId(), false);

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
    }

    @Test
    void elSchedulerFinalizaLosEventosVencidos() {
        Evento vencido = evento(UUID.randomUUID(), UUID.randomUUID());
        vencido.setEstado(EstadoEvento.PUBLICADO);
        Evento agotado = evento(UUID.randomUUID(), UUID.randomUUID());
        agotado.setEstado(EstadoEvento.AGOTADO);
        when(eventoRepository.findByEstadoInAndFechaBefore(anyList(), any()))
                .thenReturn(List.of(vencido, agotado));

        int finalizados = eventService.finalizarEventosVencidos();

        assertThat(finalizados).isEqualTo(2);
        assertThat(vencido.getEstado()).isEqualTo(EstadoEvento.FINALIZADO);
        assertThat(agotado.getEstado()).isEqualTo(EstadoEvento.FINALIZADO);
        verify(eventoRepository).saveAll(anyList());
    }

    @Test
    void elReintentoDeCancelacionesResuelveSoloLasQueResponden() {
        Evento pendiente = evento(UUID.randomUUID(), UUID.randomUUID());
        pendiente.setEstado(EstadoEvento.CANCELADO);
        pendiente.setCancelacionPendiente(true);
        Evento caido = evento(UUID.randomUUID(), UUID.randomUUID());
        caido.setEstado(EstadoEvento.CANCELADO);
        caido.setCancelacionPendiente(true);
        when(eventoRepository.findByEstadoAndCancelacionPendienteTrue(EstadoEvento.CANCELADO))
                .thenReturn(List.of(pendiente, caido));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doAnswer(invocation -> {
            if (caido.getId().equals(invocation.getArgument(0))) {
                throw new RuntimeException("orders caído");
            }
            return null;
        }).when(ordersClient).cancelarEvento(any());

        int resueltas = eventService.reintentarCancelacionesPendientes();

        assertThat(resueltas).isEqualTo(1);
        assertThat(pendiente.isCancelacionPendiente()).isFalse();
        assertThat(caido.isCancelacionPendiente()).isTrue();
    }

    private Lugar lugar(UUID id, String nombre) {
        Lugar lugar = new Lugar();
        lugar.setId(id);
        lugar.setNombre(nombre);
        lugar.setCiudad("Tandil");
        return lugar;
    }

    private Evento evento(UUID organizadorId, UUID lugarId) {
        Evento evento = new Evento();
        evento.setId(UUID.randomUUID());
        evento.setNombre("Recital");
        evento.setTipo(TipoEvento.RECITAL);
        evento.setOrganizadorId(organizadorId);
        evento.setLugarId(lugarId);
        evento.setFecha(OffsetDateTime.parse("2027-01-15T21:00:00-03:00"));
        evento.setEstado(EstadoEvento.BORRADOR);
        evento.setLimitePorCliente(4);
        return evento;
    }

    private Sector sector(UUID lugarId, String nombre) {
        Sector sector = new Sector();
        sector.setId(UUID.randomUUID());
        sector.setLugarId(lugarId);
        sector.setNombre(nombre);
        sector.setTipo(TipoSector.NUMERADO);
        sector.setCapacidad(10);
        sector.setFilas(2);
        sector.setAsientosPorFila(5);
        return sector;
    }
}
