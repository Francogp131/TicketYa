package ar.edu.unicen.ticketya.notification.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta propuesta para GET /internal/preferences?artista=&ciudad=.
 * clienteEmail es opcional: el servicio de preferencias no lo administra hoy y
 * el sender mock usa el fallback configurado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreferenciaCliente(String clienteId, String clienteEmail) {
}
