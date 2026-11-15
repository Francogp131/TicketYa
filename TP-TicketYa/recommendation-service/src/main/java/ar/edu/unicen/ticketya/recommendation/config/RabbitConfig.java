package ar.edu.unicen.ticketya.recommendation.config;

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
 * Topología de la tarea 1.5 para el historial de recomendaciones:
 * exchange topic -> recommendation.history -> DLQ.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ticketya.events";
    public static final String DLX = "ticketya.events.dlx";
    public static final String ROUTING_KEY = "orden.pagada";
    public static final String DLQ = "recommendation.history.dlq";

    @Bean
    public TopicExchange ticketyaEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange ticketyaEventsDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue recommendationHistoryQueue(
            @Value("${recommendation.rabbit.queue:recommendation.history}") String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(queueName)
                .build();
    }

    @Bean
    public Queue recommendationHistoryDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding recommendationHistoryBinding(
            @Qualifier("recommendationHistoryQueue") Queue recommendationHistoryQueue,
            TopicExchange ticketyaEventsExchange) {
        return BindingBuilder.bind(recommendationHistoryQueue)
                .to(ticketyaEventsExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    public Binding recommendationHistoryDlqBinding(
            @Qualifier("recommendationHistoryDlq") Queue recommendationHistoryDlq,
            DirectExchange ticketyaEventsDlx,
            @Value("${recommendation.rabbit.queue:recommendation.history}") String queueName) {
        return BindingBuilder.bind(recommendationHistoryDlq)
                .to(ticketyaEventsDlx)
                .with(queueName);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
