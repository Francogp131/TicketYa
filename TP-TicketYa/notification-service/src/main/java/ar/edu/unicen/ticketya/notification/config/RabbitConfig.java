package ar.edu.unicen.ticketya.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de la tarea 1.5 para las notificaciones de compra:
 * exchange topic -> notification.order -> notification.order.dlq.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ticketya.events";
    public static final String DLX = "ticketya.events.dlx";
    public static final String ROUTING_KEY_ORDEN_PAGADA = "orden.pagada";

    @Bean
    public TopicExchange ticketyaEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange ticketyaEventsDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue notificationOrderQueue(
            @Value("${notification.rabbit.queue:notification.order}") String queueName,
            @Value("${notification.rabbit.dlq:notification.order.dlq}") String dlqName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(dlqName)
                .build();
    }

    @Bean
    public Queue notificationOrderDlq(
            @Value("${notification.rabbit.dlq:notification.order.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding notificationOrderBinding(
            @Qualifier("notificationOrderQueue") Queue notificationOrderQueue,
            TopicExchange ticketyaEventsExchange) {
        return BindingBuilder.bind(notificationOrderQueue)
                .to(ticketyaEventsExchange)
                .with(ROUTING_KEY_ORDEN_PAGADA);
    }

    @Bean
    public Binding notificationOrderDlqBinding(
            @Qualifier("notificationOrderDlq") Queue notificationOrderDlq,
            DirectExchange ticketyaEventsDlx,
            @Value("${notification.rabbit.dlq:notification.order.dlq}") String dlqName) {
        return BindingBuilder.bind(notificationOrderDlq)
                .to(ticketyaEventsDlx)
                .with(dlqName);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
