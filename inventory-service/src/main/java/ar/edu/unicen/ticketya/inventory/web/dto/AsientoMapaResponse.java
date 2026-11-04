package ar.edu.unicen.ticketya.inventory.web.dto;

import java.util.UUID;

public record AsientoMapaResponse(UUID asientoId, int fila, int numero, String estado) {
}
