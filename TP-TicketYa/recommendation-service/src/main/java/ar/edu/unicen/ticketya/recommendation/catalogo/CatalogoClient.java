package ar.edu.unicen.ticketya.recommendation.catalogo;

import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import java.time.Instant;
import java.util.List;

public interface CatalogoClient {

    /**
     * Candidatos publicados y futuros. Puede lanzar CatalogoUnavailableException;
     * el servicio decide el fallback.
     */
    List<EventoCandidato> buscarCandidatos(Preferencia preferencia, Instant fechaDesde);
}
