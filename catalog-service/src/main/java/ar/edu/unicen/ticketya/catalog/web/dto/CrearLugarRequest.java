package ar.edu.unicen.ticketya.catalog.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CrearLugarRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 255) String direccion,
        @NotBlank @Size(max = 120) String ciudad,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitud,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitud) {
}
