package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.UUID;

public interface OrdersClient {

    /**
     * Avisa a orders-service que un evento fue cancelado para que inicie los reintegros.
     */
    void cancelarEvento(UUID eventoId);
}
