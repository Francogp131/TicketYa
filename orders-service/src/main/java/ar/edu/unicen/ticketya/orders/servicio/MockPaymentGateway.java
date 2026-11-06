package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.MedioPago;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pasarela mock de la Entrega 1 (tarea 4.3). El token define el resultado:
 * APROBADO, RECHAZADO o LENTO (timeout simulado).
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult cobrar(UUID ordenId, BigDecimal monto, MedioPago medioPago, String token) {
        String resultado = token == null || token.isBlank() ? "APROBADO" : token.trim().toUpperCase();
        return switch (resultado) {
            case "APROBADO" -> new PaymentResult(EstadoPago.APROBADO, "mock-" + ordenId);
            case "RECHAZADO" -> new PaymentResult(EstadoPago.RECHAZADO, "mock-" + ordenId);
            case "LENTO" -> throw new PaymentGatewayException("La pasarela de pagos no respondió a tiempo");
            default -> throw new PaymentGatewayException("Resultado de pasarela desconocido: " + token);
        };
    }

    @Override
    public void reintegrar(UUID pagoId) {
        // El mock no mueve dinero: el estado REINTEGRADO del pago es la evidencia.
    }
}
