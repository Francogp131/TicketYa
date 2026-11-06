package ar.edu.unicen.ticketya.catalog.servicio;

import java.util.UUID;

public interface OrdersClient {

    void cancelarEvento(UUID eventoId);
}
