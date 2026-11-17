package ar.edu.unicen.ticketya.notification.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * Snapshot de una compra pagada publicado por orders-service (tarea 6.3).
 * clienteEmail es opcional y aditivo: si no viene, el sender mock usa una
 * dirección determinística para poder registrar el envío en desarrollo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrdenPagadaPayload(
        String ordenId,
        String clienteId,
        String eventoId,
        String eventoNombre,
        String eventoTipo,
        List<String> artistas,
        String ciudad,
        Instant fechaEvento,
        String estado,
        String clienteEmail) {

    public static final String ESTADO_PAGADA = "PAGADA";
}
