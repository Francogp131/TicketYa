package ar.edu.unicen.ticketya.catalog.web.dto;

import jakarta.validation.constraints.NotNull;

public record ActualizarDisponibilidadRequest(@NotNull Boolean agotado) {
}
