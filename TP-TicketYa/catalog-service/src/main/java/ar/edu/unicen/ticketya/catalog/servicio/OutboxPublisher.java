package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.catalog.dominio.OutboxStatus;
import ar.edu.unicen.ticketya.catalog.eventos.CatalogEvents;
import ar.edu.unicen.ticketya.catalog.repositorio.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseBackoffSeconds;

    public OutboxPublisher(OutboxEventRepository repository,
                           RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper,
                           Clock clock,
                           @Value("${catalog.outbox.batch-size:50}") int batchSize,
                           @Value("${catalog.outbox.max-attempts:10}") int maxAttempts,
                           @Value("${catalog.outbox.base-backoff-seconds:5}") long baseBackoffSeconds) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoffSeconds = baseBackoffSeconds;
    }

    @Scheduled(fixedDelayString = "${catalog.outbox.publish-ms:2000}")
    @Transactional
    public void publishPending() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<OutboxEvent> events = repository.lockPending(now, batchSize);
        for (OutboxEvent event : events) {
            try {
                Map<String, Object> message = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
                rabbitTemplate.convertAndSend(CatalogEvents.EXCHANGE, event.getType(), message);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(now);
                event.setLastError(null);
            } catch (Exception ex) {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(truncate(ex.getMessage()));
                if (attempts >= maxAttempts) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.warn("Outbox event {} agotó reintentos: {}", event.getEventId(), ex.getMessage());
                } else {
                    long delay = baseBackoffSeconds * attempts;
                    event.setNextAttemptAt(now.plusSeconds(delay));
                    event.setStatus(OutboxStatus.PENDING);
                }
            }
        }
    }

    private String truncate(String msg) {
        if (msg == null) return null;
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}