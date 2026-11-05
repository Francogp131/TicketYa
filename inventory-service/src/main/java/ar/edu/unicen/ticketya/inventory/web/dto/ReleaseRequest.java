package ar.edu.unicen.ticketya.inventory.web.dto;

/**
 * Motivo por el que orders pide liberar una reserva (pago rechazado, error, etc.).
 * Es informativo: la liberación siempre es idempotente.
 */
public record ReleaseRequest(String motivo) {
}
