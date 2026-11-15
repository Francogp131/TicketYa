package ar.edu.unicen.ticketya.recommendation.catalogo;

/**
 * Se lanza cuando catalog no responde o devuelve un error transitorio.
 * RecommendationService cae al snapshot de eventos_vistos.
 */
public class CatalogoUnavailableException extends RuntimeException {

    public CatalogoUnavailableException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
