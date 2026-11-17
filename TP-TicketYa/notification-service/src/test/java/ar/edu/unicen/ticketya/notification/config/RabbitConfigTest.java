package ar.edu.unicen.ticketya.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void laColaDeComprasDeclaraSuDeadLetterExchangeYRoutingKey() {
        Queue cola = config.notificationOrderQueue("notification.order", "notification.order.dlq");

        assertThat(cola.isDurable()).isTrue();
        assertThat(cola.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitConfig.DLX)
                .containsEntry("x-dead-letter-routing-key", "notification.order.dlq");
    }

    @Test
    void laDlqSeEnlazaAlDlxConSuPropioNombre() {
        Queue dlq = config.notificationOrderDlq("notification.order.dlq");

        assertThat(config.notificationOrderDlqBinding(dlq, config.ticketyaEventsDlx(),
                "notification.order.dlq").getRoutingKey()).isEqualTo("notification.order.dlq");
        assertThat(config.notificationOrderBinding(config.notificationOrderQueue(
                "notification.order", "notification.order.dlq"), config.ticketyaEventsExchange())
                .getRoutingKey()).isEqualTo(RabbitConfig.ROUTING_KEY_ORDEN_PAGADA);
    }
}
