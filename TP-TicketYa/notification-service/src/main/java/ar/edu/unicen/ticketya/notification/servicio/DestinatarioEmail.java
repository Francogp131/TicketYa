package ar.edu.unicen.ticketya.notification.servicio;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resuelve el destinatario de los avisos. Como el mock no envía emails reales,
 * usa una dirección determinística cuando el evento no trae clienteEmail.
 */
@Component
public class DestinatarioEmail {

    private final String mockEmailDomain;

    public DestinatarioEmail(@Value("${notification.mock.email-domain:ticketya.local}") String mockEmailDomain) {
        this.mockEmailDomain = mockEmailDomain;
    }

    public String resolver(String email, UUID clienteId) {
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return "cliente-" + clienteId + "@" + mockEmailDomain;
    }
}
