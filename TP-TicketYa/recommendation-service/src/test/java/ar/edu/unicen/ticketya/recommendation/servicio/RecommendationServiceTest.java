package ar.edu.unicen.ticketya.recommendation.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import ar.edu.unicen.ticketya.recommendation.catalogo.CatalogoClient;
import ar.edu.unicen.ticketya.recommendation.catalogo.CatalogoUnavailableException;
import ar.edu.unicen.ticketya.recommendation.catalogo.EventoCandidato;
import ar.edu.unicen.ticketya.recommendation.dominio.EventoVisto;
import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import ar.edu.unicen.ticketya.recommendation.repositorio.EventoVistoRepository;
import ar.edu.unicen.ticketya.recommendation.repositorio.HistorialCompraRepository;
import ar.edu.unicen.ticketya.recommendation.repositorio.PreferenciaRepository;
import ar.edu.unicen.ticketya.recommendation.web.dto.RecomendacionItem;
import ar.edu.unicen.ticketya.recommendation.web.dto.RecomendacionesResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-15T12:00:00Z");
    private static final UUID CLIENTE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String OTRO = "44444444-4444-4444-8444-444444444444";
    private static final String E1 = "e0000000-0000-4000-8000-000000000001";
    private static final String E2 = "e0000000-0000-4000-8000-000000000002";
    private static final String E3 = "e0000000-0000-4000-8000-000000000003";
    private static final String E4 = "e0000000-0000-4000-8000-000000000004";

    @Mock
    private PreferenciaRepository preferenciaRepository;
    @Mock
    private HistorialCompraRepository historialRepository;
    @Mock
    private EventoVistoRepository eventoVistoRepository;
    @Mock
    private CatalogoClient catalogoClient;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(preferenciaRepository, historialRepository,
                eventoVistoRepository, catalogoClient, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void ordenaPorPreferenciasPopularidadYCoCompraYExcluyeLoComprado() {
        when(preferenciaRepository.findByClienteId(CLIENTE.toString())).thenReturn(Optional.of(preferencia()));
        when(historialRepository.findByClienteId(CLIENTE.toString()))
                .thenReturn(List.of(compra(CLIENTE.toString(), E1)));
        when(historialRepository.findByEventoIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            if (ids.size() == 1 && ids.contains(E1)) {
                // Consulta de co-compra: otros clientes que compraron E1.
                return List.of(compra(OTRO, E1), compra(CLIENTE.toString(), E1));
            }
            // Popularidad de los candidatos.
            return List.of(compra(OTRO, E1), compra(OTRO, E2), compra(OTRO, E2));
        });
        when(historialRepository.findByClienteIdIn(anyCollection()))
                .thenReturn(List.of(compra(OTRO, E1), compra(OTRO, E2)));
        when(catalogoClient.buscarCandidatos(any(), any())).thenReturn(List.of(
                candidato(E1, "Festival de Rock", "FESTIVAL", List.of("Los Piojos"), "Tandil", "2027-03-15T00:00:00Z"),
                candidato(E2, "El Avaro", "TEATRO", List.of("Compañía Teatral Tandil"), "Tandil", "2027-04-19T00:00:00Z"),
                candidato(E3, "Charla abierta", "CONFERENCIA", List.of("Dra. Ana Pérez"), "Tandil", "2027-05-20T00:00:00Z"),
                candidato(E4, "Festival de Verano", "FESTIVAL", List.of("Los Piojos"), "Tandil", "2027-06-01T00:00:00Z")));

        RecomendacionesResponse response = recommendationService.recomendar(CLIENTE);

        assertThat(response.clienteId()).isEqualTo(CLIENTE.toString());
        assertThat(response.generadoEn()).isEqualTo(AHORA);
        assertThat(response.recomendaciones()).extracting(RecomendacionItem::eventoId)
                .containsExactly(E4, E2, E3);
        assertThat(response.recomendaciones().get(0).motivos())
                .anyMatch(motivo -> motivo.contains("tipo favorito"))
                .anyMatch(motivo -> motivo.contains("Artista favorito"));
        assertThat(response.recomendaciones().get(1).motivos())
                .anyMatch(motivo -> motivo.contains("Quienes compraron"));
    }

    @Test
    void caeAlSnapshotLocalCuandoCatalogNoResponde() {
        when(preferenciaRepository.findByClienteId(CLIENTE.toString())).thenReturn(Optional.empty());
        when(historialRepository.findByClienteId(CLIENTE.toString())).thenReturn(List.of());
        when(catalogoClient.buscarCandidatos(any(), any()))
                .thenThrow(new CatalogoUnavailableException("caído", new RuntimeException("timeout")));
        when(eventoVistoRepository.findByEstadoInAndFechaEventoAfter(any(), any()))
                .thenReturn(List.of(eventoVisto(E1, "Festival", "FESTIVAL", "Tandil", "2027-03-15T00:00:00Z"),
                        eventoVisto(E2, "El Avaro", "TEATRO", "Tandil", "2027-04-19T00:00:00Z")));
        when(historialRepository.findByEventoIdIn(anyCollection())).thenReturn(List.of(
                compra(OTRO, E1), compra(OTRO, E1), compra(OTRO, E2)));

        RecomendacionesResponse response = recommendationService.recomendar(CLIENTE);

        assertThat(response.recomendaciones()).extracting(RecomendacionItem::eventoId)
                .containsExactly(E1, E2);
        assertThat(response.recomendaciones().get(0).motivos())
                .anyMatch(motivo -> motivo.contains("Popular"));
    }

    @Test
    void unClienteNuevoRecibeLosPopulares() {
        when(preferenciaRepository.findByClienteId(CLIENTE.toString())).thenReturn(Optional.empty());
        when(historialRepository.findByClienteId(CLIENTE.toString())).thenReturn(List.of());
        when(catalogoClient.buscarCandidatos(any(), any())).thenReturn(List.of(
                candidato(E1, "Festival", "FESTIVAL", List.of("A"), "Tandil", "2027-03-15T00:00:00Z"),
                candidato(E2, "El Avaro", "TEATRO", List.of("B"), "Tandil", "2027-04-19T00:00:00Z")));
        when(historialRepository.findByEventoIdIn(anyCollection())).thenReturn(List.of(
                compra(OTRO, E1), compra(OTRO, E1), compra(OTRO, E2)));

        RecomendacionesResponse response = recommendationService.recomendar(CLIENTE);

        assertThat(response.recomendaciones()).extracting(RecomendacionItem::eventoId)
                .containsExactly(E1, E2);
        assertThat(response.recomendaciones().get(0).motivos())
                .anyMatch(motivo -> motivo.contains("Popular: 2"));
    }

    @Test
    void sinCandidatosDevuelveListaVacia() {
        when(preferenciaRepository.findByClienteId(CLIENTE.toString())).thenReturn(Optional.empty());
        when(historialRepository.findByClienteId(CLIENTE.toString())).thenReturn(List.of());
        when(catalogoClient.buscarCandidatos(any(), any())).thenReturn(List.of());

        RecomendacionesResponse response = recommendationService.recomendar(CLIENTE);

        assertThat(response.recomendaciones()).isEmpty();
    }

    @Test
    void excluyeCanceladosYEventosPasados() {
        when(preferenciaRepository.findByClienteId(CLIENTE.toString())).thenReturn(Optional.empty());
        when(historialRepository.findByClienteId(CLIENTE.toString())).thenReturn(List.of());
        when(catalogoClient.buscarCandidatos(any(), any())).thenReturn(List.of(
                candidato(E1, "Pasado", "FESTIVAL", List.of(), "Tandil", "2025-01-01T00:00:00Z"),
                new EventoCandidato(E2, "Cancelado", "TEATRO", List.of(), "Tandil",
                        Instant.parse("2027-04-19T00:00:00Z"), "CANCELADO", false),
                candidato(E3, "Vigente", "TEATRO", List.of(), "Tandil", "2027-05-20T00:00:00Z")));
        when(historialRepository.findByEventoIdIn(anyCollection())).thenReturn(List.of());

        RecomendacionesResponse response = recommendationService.recomendar(CLIENTE);

        assertThat(response.recomendaciones()).extracting(RecomendacionItem::eventoId)
                .containsExactly(E3);
    }

    private Preferencia preferencia() {
        Preferencia preferencia = new Preferencia();
        preferencia.setClienteId(CLIENTE.toString());
        preferencia.setTipos(List.of("FESTIVAL", "RECITAL"));
        preferencia.setArtistas(List.of("Los Piojos"));
        preferencia.setCiudad("Tandil");
        return preferencia;
    }

    private EventoCandidato candidato(String eventoId, String nombre, String tipo, List<String> artistas,
                                      String ciudad, String fecha) {
        return new EventoCandidato(eventoId, nombre, tipo, artistas, ciudad,
                Instant.parse(fecha), "PUBLICADO", false);
    }

    private EventoVisto eventoVisto(String eventoId, String nombre, String tipo, String ciudad, String fecha) {
        EventoVisto evento = new EventoVisto();
        evento.setEventoId(eventoId);
        evento.setNombre(nombre);
        evento.setTipo(tipo);
        evento.setArtistas(List.of());
        evento.setCiudad(ciudad);
        evento.setFechaEvento(Instant.parse(fecha));
        evento.setEstado("PUBLICADO");
        return evento;
    }

    private HistorialCompra compra(String clienteId, String eventoId) {
        HistorialCompra compra = new HistorialCompra();
        compra.setClienteId(clienteId);
        compra.setEventoId(eventoId);
        compra.setTipo("FESTIVAL");
        compra.setArtistas(List.of("Los Piojos"));
        compra.setCiudad("Tandil");
        compra.setFechaEvento(Instant.parse("2027-03-15T00:00:00Z"));
        return compra;
    }
}
