package ar.edu.unicen.ticketya.recommendation.web;

import ar.edu.unicen.ticketya.recommendation.servicio.RecommendationService;
import ar.edu.unicen.ticketya.recommendation.web.dto.RecomendacionesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recomendaciones", description = "Sugerencias personalizadas del cliente")
@SecurityRequirement(name = "X-User-Id")
@SecurityRequirement(name = "X-User-Role")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    @Operation(summary = "Mis recomendaciones",
            description = "Devuelve eventos publicados y futuros ordenados por afinidad, popularidad "
                    + "y co-compra. Cada sugerencia incluye los motivos. Si catalog no responde, "
                    + "usa el snapshot local; si no hay candidatos, devuelve una lista vacía.")
    @ApiResponse(responseCode = "200", description = "Recomendaciones del cliente")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT (FORBIDDEN)")
    public RecomendacionesResponse recomendar(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("CLIENT");
        return recommendationService.recomendar(identity.userId());
    }
}
