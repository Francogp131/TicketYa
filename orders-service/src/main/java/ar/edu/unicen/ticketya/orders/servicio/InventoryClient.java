package ar.edu.unicen.ticketya.orders.servicio;

import java.util.UUID;

public interface InventoryClient {

    ReservationDto obtenerReserva(UUID reservaId);

    void confirmarReserva(UUID reservaId);

    void liberarReserva(UUID reservaId, String motivo);

    void devolverItems(ReturnDto devolucion);
}
