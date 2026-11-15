package ar.edu.unicen.ticketya.recommendation.servicio;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ranking explicable por preferencias, popularidad y co-compra. No usa ML:
 * cada sugerencia devuelve los motivos que la justifican.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final int LIMITE = 10;
    private static final Set<String> ESTADOS_ELEGIBLES = Set.of("PUBLICADO", "AGOTADO");

    private final PreferenciaRepository preferenciaRepository;
    private final HistorialCompraRepository historialRepository;
    private final EventoVistoRepository eventoVistoRepository;
    private final CatalogoClient catalogoClient;
    private final Clock clock;

    public RecommendationService(PreferenciaRepository preferenciaRepository,
                                 HistorialCompraRepository historialRepository,
                                 EventoVistoRepository eventoVistoRepository,
                                 CatalogoClient catalogoClient,
                                 Clock clock) {
        this.preferenciaRepository = preferenciaRepository;
        this.historialRepository = historialRepository;
        this.eventoVistoRepository = eventoVistoRepository;
        this.catalogoClient = catalogoClient;
        this.clock = clock;
    }

    public RecomendacionesResponse recomendar(UUID clienteId) {
        Instant ahora = Instant.now(clock);
        String cliente = clienteId.toString();
        Preferencia preferencia = preferenciaRepository.findByClienteId(cliente).orElse(null);
        List<HistorialCompra> historial = historialRepository.findByClienteId(cliente);
        Set<String> comprados = historial.stream()
                .map(HistorialCompra::getEventoId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));

        List<EventoCandidato> candidatos = obtenerCandidatos(preferencia, ahora);
        Map<String, Long> popularidad = popularidad(candidatos);
        Map<String, Long> coCompra = coCompra(cliente, comprados);

        List<RecomendacionItem> puntuados = candidatos.stream()
                .filter(candidato -> candidato.eventoId() != null)
                .filter(candidato -> !comprados.contains(candidato.eventoId()))
                .filter(candidato -> elegible(candidato, ahora))
                .map(candidato -> puntuar(candidato, preferencia, popularidad, coCompra))
                .toList();

        List<RecomendacionItem> conAfinidad = puntuados.stream()
                .filter(item -> item.puntaje() > 0)
                .toList();
        List<RecomendacionItem> resultado = (conAfinidad.isEmpty() ? puntuados : conAfinidad).stream()
                .sorted(Comparator.comparingInt(RecomendacionItem::puntaje).reversed()
                        .thenComparing(RecomendacionItem::fechaEvento,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RecomendacionItem::nombre,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(LIMITE)
                .toList();

        return new RecomendacionesResponse(cliente, ahora, resultado);
    }

    private List<EventoCandidato> obtenerCandidatos(Preferencia preferencia, Instant ahora) {
        try {
            return catalogoClient.buscarCandidatos(preferencia, ahora);
        } catch (CatalogoUnavailableException ex) {
            log.warn("Se usa el fallback de eventos_vistos: {}", ex.getMessage());
            return fallback(preferencia, ahora);
        }
    }

    private List<EventoCandidato> fallback(Preferencia preferencia, Instant ahora) {
        List<EventoVisto> eventos = eventoVistoRepository
                .findByEstadoInAndFechaEventoAfter(ESTADOS_ELEGIBLES, ahora);
        if (preferencia != null && preferencia.getCiudad() != null
                && !preferencia.getCiudad().isBlank()) {
            List<EventoVisto> enCiudad = eventos.stream()
                    .filter(evento -> preferencia.getCiudad().equalsIgnoreCase(evento.getCiudad()))
                    .toList();
            if (!enCiudad.isEmpty()) {
                eventos = enCiudad;
            }
        }
        return eventos.stream()
                .map(evento -> new EventoCandidato(
                        evento.getEventoId(),
                        evento.getNombre(),
                        evento.getTipo(),
                        evento.getArtistas(),
                        evento.getCiudad(),
                        evento.getFechaEvento(),
                        evento.getEstado(),
                        false))
                .toList();
    }

    private boolean elegible(EventoCandidato candidato, Instant ahora) {
        boolean estadoValido = candidato.estado() != null
                && ESTADOS_ELEGIBLES.contains(candidato.estado().toUpperCase(Locale.ROOT));
        boolean fechaFutura = candidato.fechaEvento() != null
                && candidato.fechaEvento().isAfter(ahora);
        return estadoValido && fechaFutura;
    }

    private RecomendacionItem puntuar(EventoCandidato candidato,
                                       Preferencia preferencia,
                                       Map<String, Long> popularidad,
                                       Map<String, Long> coCompra) {
        int puntaje = 0;
        List<String> motivos = new ArrayList<>();

        if (preferencia != null) {
            if (coincideTipo(preferencia, candidato.tipo())) {
                puntaje += 3;
                motivos.add("Coincide con tu tipo favorito: " + candidato.tipo());
            }
            String artista = artistaCoincidente(preferencia, candidato.artistas());
            if (artista != null) {
                puntaje += 4;
                motivos.add("Artista favorito: " + artista);
            }
            if (candidato.ciudad() != null && preferencia.getCiudad() != null
                    && candidato.ciudad().equalsIgnoreCase(preferencia.getCiudad())) {
                puntaje += 2;
                motivos.add("En tu ciudad: " + candidato.ciudad());
            }
        }

        long compras = popularidad.getOrDefault(candidato.eventoId(), 0L);
        if (compras > 0) {
            puntaje += (int) Math.min(compras, 5);
            motivos.add("Popular: " + compras + " compra(s) registrada(s)");
        }

        long relacionadas = coCompra.getOrDefault(candidato.eventoId(), 0L);
        if (relacionadas > 0) {
            puntaje += (int) Math.min(relacionadas * 2, 6);
            motivos.add("Quienes compraron tus eventos también compraron este");
        }

        if (motivos.isEmpty()) {
            motivos.add("Evento publicado y próximo");
        }

        return new RecomendacionItem(
                candidato.eventoId(),
                candidato.nombre(),
                candidato.tipo(),
                candidato.artistas() == null ? List.of() : candidato.artistas(),
                candidato.ciudad(),
                candidato.fechaEvento(),
                candidato.altaDemanda(),
                puntaje,
                motivos);
    }

    private boolean coincideTipo(Preferencia preferencia, String tipo) {
        if (tipo == null) {
            return false;
        }
        return preferencia.getTipos().stream().anyMatch(valor -> valor.equalsIgnoreCase(tipo));
    }

    private String artistaCoincidente(Preferencia preferencia, List<String> artistas) {
        if (artistas == null || artistas.isEmpty()) {
            return null;
        }
        for (String artista : artistas) {
            if (preferencia.getArtistas().stream().anyMatch(valor -> valor.equalsIgnoreCase(artista))) {
                return artista;
            }
        }
        return null;
    }

    private Map<String, Long> popularidad(Collection<EventoCandidato> candidatos) {
        List<String> ids = candidatos.stream()
                .map(EventoCandidato::eventoId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> resultado = new HashMap<>();
        historialRepository.findByEventoIdIn(ids).forEach(compra ->
                resultado.merge(compra.getEventoId(), 1L, Long::sum));
        return resultado;
    }

    /**
     * Clientes que compraron alguno de los eventos del cliente también compraron
     * otros eventos; se cuenta cuántas veces aparece cada candidato.
     */
    private Map<String, Long> coCompra(String clienteId, Set<String> comprados) {
        if (comprados.isEmpty()) {
            return Map.of();
        }
        // 1) Clientes que compraron alguno de los eventos del cliente.
        Set<String> otrosClientes = historialRepository.findByEventoIdIn(comprados).stream()
                .map(HistorialCompra::getClienteId)
                .filter(id -> id != null && !clienteId.equals(id))
                .collect(Collectors.toSet());
        if (otrosClientes.isEmpty()) {
            return Map.of();
        }
        // 2) Otros eventos que compraron esos clientes, excluyendo los que ya tiene.
        Map<String, Long> resultado = new HashMap<>();
        historialRepository.findByClienteIdIn(otrosClientes).stream()
                .filter(compra -> compra.getEventoId() != null && !comprados.contains(compra.getEventoId()))
                .forEach(compra -> resultado.merge(compra.getEventoId(), 1L, Long::sum));
        return resultado;
    }
}
