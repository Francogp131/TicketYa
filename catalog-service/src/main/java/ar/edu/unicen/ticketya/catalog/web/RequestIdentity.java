package ar.edu.unicen.ticketya.catalog.web;

import ar.edu.unicen.ticketya.catalog.web.ApiException;
import java.util.UUID;

/**
 * Identidad de la Entrega 1: el gateway propaga X-User-Id y X-User-Role.
 * El servicio vuelve a validar para que no se pueda escribir directo al puerto interno.
 */
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

    public void requireRole(String expectedRole) {
        if (!expectedRole.equals(role)) {
            throw ApiException.forbidden("FORBIDDEN", "El rol " + role + " no puede ejecutar esta operación");
        }
    }

    public void requireOwner(UUID ownerId, String code) {
        if (!userId.equals(ownerId)) {
            throw ApiException.forbidden(code, "El recurso pertenece a otro usuario");
        }
    }
}
