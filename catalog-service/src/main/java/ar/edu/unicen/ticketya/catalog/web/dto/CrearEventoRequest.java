package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "CrearEventoRequest", description = "Datos para crear un evento en BORRADOR")
public record CrearEventoRequest(
        @Schema(description = "Nombre del evento", example = "Festival de Verano")
        @NotBlank @Size(max = 200) String nombre,
        @Schema(description = "Descripción", example = "Una noche de música")
        @Size(max = 2000) String descripcion,
        @Schema(description = "Tipo de evento", example = "FESTIVAL")
        @NotNull TipoEvento tipo,
        @Schema(description = "Lugar donde se realiza", example = "5c9b1f3e-2a4d-4f5e-9a8b-1c2d3e4f5a6b")
        @NotNull UUID lugarId,
        @Schema(description = "Fecha y hora futura con offset", example = "2027-01-15T21:00:00-03:00")
        @NotNull OffsetDateTime fecha,
        @Schema(description = "Artistas o participantes", example = "[\"Artista A\", \"Artista B\"]")
        @NotEmpty List<@NotBlank @Size(max = 150) String> participantes,
        @Schema(description = "Máximo de entradas por cliente", example = "4")
        @Min(1) Integer limitePorCliente) {
}
