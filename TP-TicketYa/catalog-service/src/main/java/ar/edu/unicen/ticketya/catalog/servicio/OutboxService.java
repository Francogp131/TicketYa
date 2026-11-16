package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.dominio.OutboxEvent;
import ar.edu.unicen.ticketya.catalog.eventos.EventEnvelope;
import ar.edu.unicen.ticketya.catalog.repositorio.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void register(String type, UUID aggregateId, String correlationId, Map<String, Object> payload) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(eventId, type, now.toString(), correlationId, 1, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope.asMap());
            repository.save(new OutboxEvent(UUID.randomUUID(), eventId, type, aggregateId, now, correlationId, 1, json, now));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento de outbox " + type, e);
        }
    }
}