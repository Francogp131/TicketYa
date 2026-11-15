package ar.edu.unicen.ticketya.recommendation.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ar.edu.unicen.ticketya.recommendation.config.RabbitConfig;
import ar.edu.unicen.ticketya.recommendation.dominio.HistorialCompra;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica el consumidor real de RabbitMQ: una orden pagada se registra una vez,
 * el duplicado no duplica el historial y un reintegro no se registra.
 */
@SpringBootTest(properties = {
        "recommendation.seed.enabled=false",
        "recommendation.mongo.ensure-indexes=true",
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
@Testcontainers(disabledWithoutDocker = true)
class RecommendationRabbitIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void consumeOrdenPagadaUnaVezYDescartaDuplicadosYReintegros() {
        String eventId = UUID.randomUUID().toString();
        String clienteId = UUID.randomUUID().toString();
        String eventoId = UUID.randomUUID().toString();
        OrdenPagadaEvent event = new OrdenPagadaEvent(eventId, OrdenPagadaEvent.TIPO,
                Instant.parse("2026-11-15T12:00:00Z"), UUID.randomUUID().toString(), 1,
                new OrdenPagadaPayload(UUID.randomUUID().toString(), clienteId, eventoId,
                        "Festival de Rock", "FESTIVAL", List.of("Los Piojos"), "Tandil",
                        Instant.parse("2027-03-15T00:00:00Z"), OrdenPagadaPayload.ESTADO_PAGADA));

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(cantidadHistorial(eventId, clienteId, eventoId)).isEqualTo(1));

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(cantidadHistorial(eventId, clienteId, eventoId)).isEqualTo(1));

        OrdenPagadaEvent reintegro = new OrdenPagadaEvent(UUID.randomUUID().toString(),
                "orden.reintegrada", Instant.now(), UUID.randomUUID().toString(), 1,
                event.payload());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "orden.reintegrada", reintegro);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(cantidadHistorial(eventId, clienteId, eventoId)).isEqualTo(1));
    }

    private long cantidadHistorial(String eventId, String clienteId, String eventoId) {
        return mongoTemplate.count(Query.query(Criteria.where("eventId").is(eventId)
                .and("clienteId").is(clienteId)
                .and("eventoId").is(eventoId)), HistorialCompra.class);
    }
}
