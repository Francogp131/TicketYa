package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.web.ApiException;
import java.util.Arrays;
import java.util.UUID;

public record RequestIdentity(UUID userId, String role, String email) {

    public static RequestIdentity require(String rawUserId, String rawRole) {
        return require(rawUserId, rawRole, null);
    }

    public static RequestIdentity require(String rawUserId, String rawRole, String rawEmail) {
        if (rawUserId == null || rawUserId.isBlank() || rawRole == null || rawRole.isBlank()) {
            throw ApiException.unauthorized("Faltan los encabezados X-User-Id y X-User-Role");
        }
        try {
            String email = (rawEmail == null || rawEmail.isBlank()) ? null : rawEmail.trim();
            return new RequestIdentity(UUID.fromString(rawUserId.trim()), rawRole.trim().toUpperCase(), email);
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("X-User-Id debe ser un UUID válido");
        }
    }

    public void requireRole(String... expectedRoles) {
        if (expectedRoles == null || expectedRoles.length == 0) {
            return;
        }
        boolean ok = Arrays.stream(expectedRoles).anyMatch(r -> r != null && r.equalsIgnoreCase(role));
        if (!ok) {
            throw ApiException.forbidden("FORBIDDEN", "El rol " + role + " no puede ejecutar esta operación");
        }
    }

    public void requireOwner(UUID ownerId, String code) {
        if (!userId.equals(ownerId)) {
            throw ApiException.forbidden(code, "El recurso pertenece a otro usuario");
        }
    }
}