package ar.edu.unicen.ticketya.notification.servicio;

import ar.edu.unicen.ticketya.notification.dominio.Notificacion;

public interface NotificationSender {
    void send(Notificacion notificacion) throws Exception;
}
