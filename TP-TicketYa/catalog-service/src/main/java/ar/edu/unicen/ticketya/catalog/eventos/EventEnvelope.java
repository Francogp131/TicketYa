package ar.edu.unicen.ticketya.catalog.eventos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String type,
        String occurredAt,
        String correlationId,
        int version,
        Map<String, Object> payload
) {
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", eventId);
        map.put("type", type);
        map.put("occurredAt", occurredAt);
        map.put("correlationId", correlationId);
        map.put("version", version);
        map.put("payload", payload == null ? Map.of() : payload);
        return map;
    }
}