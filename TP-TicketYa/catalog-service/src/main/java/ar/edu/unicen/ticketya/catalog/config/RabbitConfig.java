package ar.edu.unicen.ticketya.catalog.config;

import ar.edu.unicen.ticketya.catalog.eventos.CatalogEvents;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange ticketyaEventsExchange() {
        return new TopicExchange(CatalogEvents.EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}