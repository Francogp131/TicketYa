package ar.edu.unicen.ticketya.catalog.servicio;

import ar.edu.unicen.ticketya.catalog.web.ApiException;

enum OrdenBusqueda {
    FECHA_ASC,
    FECHA_DESC,
    PRECIO_ASC,
    PRECIO_DESC,
    RELEVANCIA;

    static OrdenBusqueda parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FECHA_ASC;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_SORT", "El orden solicitado no es válido");
        }
    }
}
