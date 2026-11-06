package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "CrearSectorRequest", description = "Datos para crear un sector")
public record CrearSectorRequest(
        @Schema(description = "Nombre del sector", example = "Platea A")
        @NotBlank @Size(max = 120) String nombre,
        @Schema(description = "NUMERADO o NO_NUMERADO", example = "NUMERADO")
        @NotNull TipoSector tipo,
        @Schema(description = "Capacidad para sectores no numerados; en numerados se calcula filas * asientosPorFila",
                example = "200")
        @Min(1) Integer capacidad,
        @Schema(description = "Filas (solo NUMERADO)", example = "10")
        @Min(1) Integer filas,
        @Schema(description = "Asientos por fila (solo NUMERADO)", example = "20")
        @Min(1) Integer asientosPorFila) {
}
