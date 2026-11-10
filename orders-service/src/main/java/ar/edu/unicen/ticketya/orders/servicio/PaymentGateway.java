package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pasarela de pagos reemplazable (tarea 4.3). La implementación de la Entrega 1
 * es un mock controlado por el token que manda el cliente.
 */
public interface PaymentGateway {

    PaymentResult cobrar(UUID ordenId, BigDecimal monto, MedioPago medioPago, String token);

    /**
     * Solicita el reintegro de un pago por un monto específico. Es idempotente
     * por (pagoId, monto/entrada) según lo maneje el llamador.
     */
    void reintegrar(UUID pagoId, java.math.BigDecimal monto);

    record PaymentResult(EstadoPago estado, String externalId) {
    }
}
