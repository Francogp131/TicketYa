package ar.edu.unicen.ticketya.catalog.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "CrearLugarRequest", description = "Datos para crear un lugar")
public record CrearLugarRequest(
        @Schema(description = "Nombre del lugar", example = "Estadio Municipal")
        @NotBlank @Size(max = 150) String nombre,
        @Schema(description = "Dirección", example = "Av. Siempre Viva 742")
        @NotBlank @Size(max = 255) String direccion,
        @Schema(description = "Ciudad", example = "Tandil")
        @NotBlank @Size(max = 120) String ciudad,
        @Schema(description = "Latitud entre -90 y 90", example = "-37.3217")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitud,
        @Schema(description = "Longitud entre -180 y 180", example = "-59.1332")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitud) {
}
