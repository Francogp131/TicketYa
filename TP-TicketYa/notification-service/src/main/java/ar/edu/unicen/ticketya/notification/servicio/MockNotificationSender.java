package ar.edu.unicen.ticketya.notification.servicio;

import ar.edu.unicen.ticketya.notification.dominio.Notificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Canal simulado para desarrollo/pruebas. No envía mensajes reales.
 */
@Component
public class MockNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationSender.class);

    @Override
    public void send(Notificacion notificacion) {
        if (notificacion.getDestinatarioEmail() == null || notificacion.getDestinatarioEmail().isBlank()) {
            throw new IllegalArgumentException("El destinatario no tiene email");
        }
        log.info("Mock envío notificación id={} tipo={} destinatario={} asunto={}",
                notificacion.getId(), notificacion.getTipo(), mask(notificacion.getDestinatarioEmail()),
                notificacion.getAsunto());
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
