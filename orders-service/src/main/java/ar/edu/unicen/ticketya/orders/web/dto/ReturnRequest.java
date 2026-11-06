package ar.edu.unicen.ticketya.orders.web.dto;

import jakarta.validation.constraints.Size;

public record ReturnRequest(
        @Size(max = 500, message = "no puede superar los 500 caracteres")
        String motivo) {
}
