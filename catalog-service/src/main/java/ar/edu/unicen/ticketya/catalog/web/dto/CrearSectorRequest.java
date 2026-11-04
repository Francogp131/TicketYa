package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.TipoSector;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearSectorRequest(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull TipoSector tipo,
        @Min(1) Integer capacidad,
        @Min(1) Integer filas,
        @Min(1) Integer asientosPorFila) {
}
