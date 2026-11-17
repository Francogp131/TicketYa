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
    public static final String ROUTING_KEY_EVENTO_ACTUALIZADO = "evento.actualizado";
    public static final String ROUTING_KEY_EVENTO_CANCELADO = "evento.cancelado";
    public static final String ROUTING_KEY_EVENTO_PUBLICADO = "evento.publicado";

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
    public Queue notificationEventQueue(
            @Value("${notification.rabbit.event-queue:notification.event}") String queueName,
            @Value("${notification.rabbit.event-dlq:notification.event.dlq}") String dlqName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(dlqName)
                .build();
    }

    @Bean
    public Queue notificationEventDlq(
            @Value("${notification.rabbit.event-dlq:notification.event.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding notificationEventActualizadoBinding(
            @Qualifier("notificationEventQueue") Queue notificationEventQueue,
            TopicExchange ticketyaEventsExchange) {
        return BindingBuilder.bind(notificationEventQueue)
                .to(ticketyaEventsExchange)
                .with(ROUTING_KEY_EVENTO_ACTUALIZADO);
    }

    @Bean
    public Binding notificationEventCanceladoBinding(
            @Qualifier("notificationEventQueue") Queue notificationEventQueue,
            TopicExchange ticketyaEventsExchange) {
        return BindingBuilder.bind(notificationEventQueue)
                .to(ticketyaEventsExchange)
                .with(ROUTING_KEY_EVENTO_CANCELADO);
    }

    @Bean
    public Binding notificationEventDlqBinding(
            @Qualifier("notificationEventDlq") Queue notificationEventDlq,
            DirectExchange ticketyaEventsDlx,
            @Value("${notification.rabbit.event-dlq:notification.event.dlq}") String dlqName) {
        return BindingBuilder.bind(notificationEventDlq)
                .to(ticketyaEventsDlx)
                .with(dlqName);
    }

    @Bean
    public Queue notificationFavoriteQueue(
            @Value("${notification.rabbit.favorite-queue:notification.favorite}") String queueName,
            @Value("${notification.rabbit.favorite-dlq:notification.favorite.dlq}") String dlqName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(dlqName)
                .build();
    }

    @Bean
    public Queue notificationFavoriteDlq(
            @Value("${notification.rabbit.favorite-dlq:notification.favorite.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding notificationFavoriteBinding(
            @Qualifier("notificationFavoriteQueue") Queue notificationFavoriteQueue,
            TopicExchange ticketyaEventsExchange) {
        return BindingBuilder.bind(notificationFavoriteQueue)
                .to(ticketyaEventsExchange)
                .with(ROUTING_KEY_EVENTO_PUBLICADO);
    }

    @Bean
    public Binding notificationFavoriteDlqBinding(
            @Qualifier("notificationFavoriteDlq") Queue notificationFavoriteDlq,
            DirectExchange ticketyaEventsDlx,
            @Value("${notification.rabbit.favorite-dlq:notification.favorite.dlq}") String dlqName) {
        return BindingBuilder.bind(notificationFavoriteDlq)
                .to(ticketyaEventsDlx)
                .with(dlqName);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
