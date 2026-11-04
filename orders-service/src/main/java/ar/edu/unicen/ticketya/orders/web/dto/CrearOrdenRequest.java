package ar.edu.unicen.ticketya.orders.web.dto;

import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CrearOrdenRequest(
        @NotNull UUID reservaId,
        @NotNull MedioPago medioPago,
        String token) {
}
