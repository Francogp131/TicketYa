package ar.edu.unicen.ticketya.catalog.web.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PrecioRequest(@NotNull BigDecimal precio) {
}
