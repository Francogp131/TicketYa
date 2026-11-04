package ar.edu.unicen.ticketya.inventory.web;

import ar.edu.unicen.ticketya.inventory.web.ApiException;
import java.util.UUID;

public record RequestIdentity(UUID userId, String role) {

    public static RequestIdentity require(String rawUserId, String rawRole) {
        if (rawUserId == null || rawUserId.isBlank() || rawRole == null || rawRole.isBlank()) {
            throw ApiException.unauthorized("Faltan los encabezados X-User-Id y X-User-Role");
        }
        try {
            return new RequestIdentity(UUID.fromString(rawUserId), rawRole.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("X-User-Id debe ser un UUID válido");
        }
    }
}
