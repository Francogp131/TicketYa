package ar.edu.unicen.ticketya.catalog.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
                asientoRepository, participanteRepository, precioRepository, inventoryClient,
                ordersClient, clock);
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
    void republicarElMismoEventoNoVuelveAEnviarElInventario() {
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

        eventService.publicar(evento.getId(), evento.getOrganizadorId());

        assertThatThrownBy(() -> eventService.publicar(evento.getId(), evento.getOrganizadorId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_EDITABLE");
                });
        verify(inventoryClient, times(1)).publicarInventario(any());
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
    void cancelarEventoPublicadoAvisaAOrdersYQuedaCancelado() {
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
    void siOrdersFallaLaCancelacionQuedaPendiente() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("orders caído")).when(ordersClient).cancelarEvento(evento.getId());

        CancelacionResponse response = eventService.cancelar(evento.getId());

        assertThat(response.estado()).isEqualTo(EstadoEvento.CANCELADO);
        assertThat(response.cancelacionPendiente()).isTrue();
        assertThat(evento.isCancelacionPendiente()).isTrue();
    }

    @Test
    void noSePuedeCancelarUnEventoFinalizado() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.FINALIZADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.cancelar(evento.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_CANCELLABLE");
                });
        verify(ordersClient, never()).cancelarEvento(any());
    }

    @Test
    void laDisponibilidadAlternaEntrePublicadoYAgotado() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));

        eventService.actualizarDisponibilidad(evento.getId(), true);
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.AGOTADO);

        eventService.actualizarDisponibilidad(evento.getId(), false);
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(eventoRepository, times(2)).save(evento);
    }

    @Test
    void unEventoEnBorradorNoPuedePasarAAgotado() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.actualizarDisponibilidad(evento.getId(), true))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("EVENT_NOT_EDITABLE");
                });
    }

    @Test
    void elSchedulerFinalizaLosEventosVencidos() {
        Evento publicado = evento(UUID.randomUUID(), UUID.randomUUID(), "Publicado",
                OffsetDateTime.parse("2026-11-01T21:00:00-03:00"), EstadoEvento.PUBLICADO);
        Evento agotado = evento(UUID.randomUUID(), UUID.randomUUID(), "Agotado",
                OffsetDateTime.parse("2026-11-02T21:00:00-03:00"), EstadoEvento.AGOTADO);
        when(eventoRepository.findByEstadoInAndFechaBefore(any(), any(OffsetDateTime.class)))
                .thenReturn(List.of(publicado, agotado));

        int finalizados = eventService.finalizarEventosVencidos();

        assertThat(finalizados).isEqualTo(2);
        assertThat(publicado.getEstado()).isEqualTo(EstadoEvento.FINALIZADO);
        assertThat(agotado.getEstado()).isEqualTo(EstadoEvento.FINALIZADO);
        verify(eventoRepository).saveAll(List.of(publicado, agotado));
    }

    @Test
    void elSchedulerReintentaLasCancelacionesPendientes() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.CANCELADO);
        evento.setCancelacionPendiente(true);
        when(eventoRepository.findByCancelacionPendienteTrue()).thenReturn(List.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        int reintentadas = eventService.reintentarCancelacionesPendientes();

        assertThat(reintentadas).isEqualTo(1);
        assertThat(evento.isCancelacionPendiente()).isFalse();
        verify(ordersClient).cancelarEvento(evento.getId());
    }

    @Test
    void buscarAplicaLosFiltrosCombinadosYCalculaElPrecioMinimo() {
        UUID organizadorId = UUID.randomUUID();
        UUID lugarId = UUID.randomUUID();
        Evento evento = evento(organizadorId, lugarId, "Festival",
                OffsetDateTime.parse("2027-03-14T21:00:00-03:00"), EstadoEvento.PUBLICADO);
        Lugar lugar = lugar(lugarId, "Estadio", "Tandil");
        when(eventoRepository.buscar(any(), eq(List.of(TipoEvento.FESTIVAL)), eq(true), eq(organizadorId),
                eq("%los piojos%"), eq("tandil"), any(), any())).thenReturn(List.of(evento));
        when(lugarRepository.findAllById(any())).thenReturn(List.of(lugar));
        when(precioRepository.findByEventoIdIn(any())).thenReturn(List.of(
                new EventoSectorPrecio(evento.getId(), UUID.randomUUID(), new BigDecimal("25000")),
                new EventoSectorPrecio(evento.getId(), UUID.randomUUID(), new BigDecimal("18000"))));

        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                TipoEvento.FESTIVAL, organizadorId, "  Los Piojos ", " Tandil ",
                null, null, "FECHA_ASC", 0, 20);

        assertThat(pagina.totalElements()).isEqualTo(1);
        assertThat(pagina.content()).singleElement().satisfies(resultado -> {
            assertThat(resultado.ciudad()).isEqualTo("Tandil");
            assertThat(resultado.lugarNombre()).isEqualTo("Estadio");
            assertThat(resultado.precioDesde()).isEqualByComparingTo("18000");
        });
    }

    @Test
    void buscarOrdenaPorPrecioDescendenteYPagina() {
        UUID organizadorId = UUID.randomUUID();
        UUID lugarId = UUID.randomUUID();
        Evento barato = evento(organizadorId, lugarId, "Barato",
                OffsetDateTime.parse("2027-01-01T21:00:00-03:00"), EstadoEvento.PUBLICADO);
        Evento caro = evento(organizadorId, lugarId, "Caro",
                OffsetDateTime.parse("2027-02-01T21:00:00-03:00"), EstadoEvento.PUBLICADO);
        Evento medio = evento(organizadorId, lugarId, "Medio",
                OffsetDateTime.parse("2027-03-01T21:00:00-03:00"), EstadoEvento.AGOTADO);
        when(eventoRepository.buscar(any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(barato, caro, medio));
        when(lugarRepository.findAllById(any())).thenReturn(List.of(lugar(lugarId, "Estadio", "Tandil")));
        when(precioRepository.findByEventoIdIn(any())).thenReturn(List.of(
                new EventoSectorPrecio(barato.getId(), UUID.randomUUID(), new BigDecimal("100")),
                new EventoSectorPrecio(caro.getId(), UUID.randomUUID(), new BigDecimal("300")),
                new EventoSectorPrecio(medio.getId(), UUID.randomUUID(), new BigDecimal("200"))));

        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                null, null, null, null, null, null, "PRECIO_DESC", 0, 2);

        assertThat(pagina.totalElements()).isEqualTo(3);
        assertThat(pagina.totalPages()).isEqualTo(2);
        assertThat(pagina.content()).extracting(EventoBusquedaResponse::nombre)
                .containsExactly("Caro", "Medio");
    }

    @Test
    void buscarOrdenaPorFechaAscendente() {
        UUID organizadorId = UUID.randomUUID();
        UUID lugarId = UUID.randomUUID();
        Evento tarde = evento(organizadorId, lugarId, "Tarde",
                OffsetDateTime.parse("2027-05-01T21:00:00-03:00"), EstadoEvento.PUBLICADO);
        Evento temprano = evento(organizadorId, lugarId, "Temprano",
                OffsetDateTime.parse("2027-01-01T21:00:00-03:00"), EstadoEvento.PUBLICADO);
        when(eventoRepository.buscar(any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(tarde, temprano));
        when(lugarRepository.findAllById(any())).thenReturn(List.of(lugar(lugarId, "Estadio", "Tandil")));
        when(precioRepository.findByEventoIdIn(any())).thenReturn(List.of());

        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                null, null, null, null, null, null, "FECHA_ASC", 0, 20);

        assertThat(pagina.content()).extracting(EventoBusquedaResponse::nombre)
                .containsExactly("Temprano", "Tarde");
    }

    @Test
    void buscarPorRelevanciaPriorizaPublicadosYLuegoLaFechaMasProxima() {
        UUID organizadorId = UUID.randomUUID();
        UUID lugarId = UUID.randomUUID();
        Evento publicadoLejano = evento(organizadorId, lugarId, "Publicado lejano",
                OffsetDateTime.parse("2027-05-01T21:00:00-03:00"), EstadoEvento.PUBLICADO);
        Evento publicadoCercano = evento(organizadorId, lugarId, "Publicado cercano",
                OffsetDateTime.parse("2027-02-01T21:00:00-03:00"), EstadoEvento.PUBLICADO);
        Evento agotadoCercano = evento(organizadorId, lugarId, "Agotado cercano",
                OffsetDateTime.parse("2027-01-01T21:00:00-03:00"), EstadoEvento.AGOTADO);
        when(eventoRepository.buscar(any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(publicadoLejano, agotadoCercano, publicadoCercano));
        when(lugarRepository.findAllById(any())).thenReturn(List.of(lugar(lugarId, "Estadio", "Tandil")));
        when(precioRepository.findByEventoIdIn(any())).thenReturn(List.of());

        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                null, null, null, null, null, null, "RELEVANCIA", 0, 20);

        assertThat(pagina.content()).extracting(EventoBusquedaResponse::nombre)
                .containsExactly("Publicado cercano", "Publicado lejano", "Agotado cercano");
    }

    @Test
    void buscarSinResultadosDevuelveListaVacia() {
        when(eventoRepository.buscar(any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        PageResponse<EventoBusquedaResponse> pagina = eventService.buscar(
                null, null, null, null, null, null, "FECHA_ASC", 0, 20);

        assertThat(pagina.content()).isEmpty();
        assertThat(pagina.totalElements()).isZero();
        assertThat(pagina.totalPages()).isZero();
    }

    @Test
    void buscarConRangoDeFechasInvalidoDevuelve400() {
        OffsetDateTime desde = OffsetDateTime.parse("2027-03-14T21:00:00-03:00");
        OffsetDateTime hasta = OffsetDateTime.parse("2027-01-14T21:00:00-03:00");

        assertThatThrownBy(() -> eventService.buscar(null, null, null, null,
                desde, hasta, "FECHA_ASC", 0, 20))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("INVALID_DATE_RANGE");
                });
    }

    @Test
    void buscarConOrdenInvalidoDevuelve400() {
        assertThatThrownBy(() -> eventService.buscar(null, null, null, null,
                null, null, "PRECIO", 0, 20))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("INVALID_SORT");
                });
    }

    @Test
    void elDetalleDevuelve503SiInventoryNoResponde() {
        Evento evento = evento(UUID.randomUUID(), UUID.randomUUID());
        evento.setEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        when(lugarRepository.findById(evento.getLugarId()))
                .thenReturn(Optional.of(lugar(evento.getLugarId(), "Estadio", "Tandil")));
        when(inventoryClient.obtenerDisponibilidad(evento.getId()))
                .thenThrow(new RuntimeException("inventory caído"));

        assertThatThrownBy(() -> eventService.detalle(evento.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(503);
                    assertThat(ex.getCode()).isEqualTo("INVENTORY_UNAVAILABLE");
                });
    }

    private Evento evento(UUID organizadorId, UUID lugarId) {
        return evento(organizadorId, lugarId, "Recital",
                OffsetDateTime.parse("2027-01-15T21:00:00-03:00"), EstadoEvento.BORRADOR);
    }

    private Evento evento(UUID organizadorId, UUID lugarId, String nombre,
                          OffsetDateTime fecha, EstadoEvento estado) {
        Evento evento = new Evento();
        evento.setId(UUID.randomUUID());
        evento.setNombre(nombre);
        evento.setTipo(TipoEvento.RECITAL);
        evento.setOrganizadorId(organizadorId);
        evento.setLugarId(lugarId);
        evento.setFecha(fecha);
        evento.setEstado(estado);
        evento.setLimitePorCliente(4);
        return evento;
    }

    private Lugar lugar(UUID lugarId, String nombre, String ciudad) {
        Lugar lugar = new Lugar();
        lugar.setId(lugarId);
        lugar.setNombre(nombre);
        lugar.setCiudad(ciudad);
        return lugar;
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
