package ar.edu.unicen.ticketya.inventory.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Disponibilidad pública de un evento. disponibles en un numerado cuenta asientos LIBRE.")
public record DisponibilidadResponse(
        @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID eventoId,
        @Schema(example = "PUBLICADO") String estado,
        List<Sector> sectores) {

    @Schema(name = "SectorDisponible")
    public record Sector(
            @Schema(example = "b0000000-0000-4000-8000-000000000001") UUID sectorId,
            @Schema(example = "Platea") String nombre,
            @Schema(example = "NUMERADO") String tipo,
            @Schema(example = "25000.00") BigDecimal precio,
            @Schema(example = "20") int capacidad,
            @Schema(example = "20") int disponibles) {
    }
}
