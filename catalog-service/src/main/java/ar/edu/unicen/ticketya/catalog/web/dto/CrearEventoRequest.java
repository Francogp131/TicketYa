package ar.edu.unicen.ticketya.catalog.web.dto;

import ar.edu.unicen.ticketya.catalog.dominio.TipoEvento;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CrearEventoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 2000) String descripcion,
        @NotNull TipoEvento tipo,
        @NotNull UUID lugarId,
        @NotNull OffsetDateTime fecha,
        @NotEmpty List<@NotBlank @Size(max = 150) String> participantes,
        @Min(1) Integer limitePorCliente) {
}
