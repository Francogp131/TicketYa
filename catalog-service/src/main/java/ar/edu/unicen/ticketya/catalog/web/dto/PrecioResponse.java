package ar.edu.unicen.ticketya.catalog.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PrecioResponse(UUID eventoId, UUID sectorId, BigDecimal precio) {
}
