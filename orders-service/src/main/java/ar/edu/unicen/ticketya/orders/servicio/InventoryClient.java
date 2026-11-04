package ar.edu.unicen.ticketya.orders.servicio;

import java.util.UUID;

public interface InventoryClient {

    ReservationDto obtenerReserva(UUID reservaId);
}
