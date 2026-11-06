package ar.edu.unicen.ticketya.orders.servicio;

/**
 * La pasarela no respondió dentro del tiempo esperado (token LENTO).
 * Se traduce a 502 PAYMENT_GATEWAY_ERROR.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }
}
