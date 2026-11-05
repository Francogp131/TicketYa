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
import ar.edu.unicen.ticketya.catalog.web.dto.CrearEventoRequest;
import ar.edu.unicen.ticketya.catalog.web.dto.EventoResponse;
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

    private EventService eventService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZONA);

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventoRepository, lugarRepository, sectorRepository,
                asientoRepository, participanteRepository, precioRepository, inventoryClient, clock);
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
