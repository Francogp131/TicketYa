package ar.edu.unicen.ticketya.inventory.web;

import ar.edu.unicen.ticketya.inventory.servicio.DisponibilidadService;
import ar.edu.unicen.ticketya.inventory.servicio.ReservaService;
import ar.edu.unicen.ticketya.inventory.web.dto.AsientoMapaResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.DisponibilidadResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservaRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import ar.edu.unicen.ticketya.inventory.web.openapi.Ejemplos;
import ar.edu.unicen.ticketya.inventory.web.openapi.Problema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventario público", description = "Disponibilidad, mapa de asientos y reservas del cliente.")
public class InventoryController {

    private final DisponibilidadService disponibilidadService;
    private final ReservaService reservaService;

    public InventoryController(DisponibilidadService disponibilidadService,
                               ReservaService reservaService) {
        this.disponibilidadService = disponibilidadService;
        this.reservaService = reservaService;
    }

    @GetMapping("/events/{eventoId}/availability")
    @Operation(summary = "Disponibilidad del evento",
            description = "Público, sin identidad. En un sector numerado, disponibles es la cantidad de asientos LIBRE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sectores con precio y disponibilidad",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisponibilidadResponse.class),
                            examples = @ExampleObject(value = Ejemplos.DISPONIBILIDAD))),
            @ApiResponse(responseCode = "404", description = "No existe el evento",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "EVENT_NOT_FOUND", value = Ejemplos.EVENTO_NO_ENCONTRADO)))
    })
    public DisponibilidadResponse disponibilidad(@PathVariable UUID eventoId) {
        return disponibilidadService.obtener(eventoId);
    }

    @GetMapping("/events/{eventoId}/seats")
    @Operation(summary = "Mapa de asientos",
            description = """
                    Público, sin identidad. Una zona no numerada devuelve una lista vacía. \
                    Un sector que no pertenece al evento responde 404 SECTOR_NOT_FOUND.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asientos del sector, ordenados por fila y número",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AsientoMapaResponse.class)),
                            examples = @ExampleObject(value = Ejemplos.ASIENTOS))),
            @ApiResponse(responseCode = "404", description = "No existe el evento o el sector",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = {
                                    @ExampleObject(name = "EVENT_NOT_FOUND", value = Ejemplos.EVENTO_NO_ENCONTRADO),
                                    @ExampleObject(name = "SECTOR_NOT_FOUND", value = Ejemplos.SECTOR_NO_ENCONTRADO)
                            }))
    })
    public List<AsientoMapaResponse> asientos(@PathVariable UUID eventoId,
                                              @Parameter(description = "Sector numerado del evento", required = true)
                                              @RequestParam UUID sectorId) {
        return disponibilidadService.asientos(eventoId, sectorId).stream()
                .map(asiento -> new AsientoMapaResponse(
                        asiento.getAsientoId(), asiento.getFila(), asiento.getNumero(),
                        asiento.getEstado().name()))
                .toList();
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reservar asientos o lugares de una zona",
            description = """
                    Todo o nada, durante 10 minutos. Un ítem numerado manda asientoIds y omite cantidad. \
                    Un ítem de zona manda cantidad y omite asientoIds. \
                    El cliente sale de X-User-Id. 409 si el asiento no está libre o la zona no alcanza. \
                    422 CLIENT_LIMIT_EXCEEDED si vendidas más reservas vigentes más lo pedido superan limite_por_cliente.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva VIGENTE creada",
                    headers = @Header(name = "Location", description = "URL de la reserva",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ReservationResponse.class),
                            examples = @ExampleObject(value = Ejemplos.RESERVA_CREADA))),
            @ApiResponse(responseCode = "400", description = "Cuerpo inválido",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", value = Ejemplos.VALIDACION))),
            @ApiResponse(responseCode = "401", description = "Falta la identidad o X-User-Id no es un UUID",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "UNAUTHORIZED", value = Ejemplos.NO_AUTENTICADO))),
            @ApiResponse(responseCode = "404", description = "No existe el evento o el sector",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = {
                                    @ExampleObject(name = "EVENT_NOT_FOUND", value = Ejemplos.EVENTO_NO_ENCONTRADO),
                                    @ExampleObject(name = "SECTOR_NOT_FOUND", value = Ejemplos.SECTOR_NO_ENCONTRADO)
                            })),
            @ApiResponse(responseCode = "409", description = "No se pudo reservar nada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = {
                                    @ExampleObject(name = "SEAT_NOT_AVAILABLE", value = Ejemplos.ASIENTO_OCUPADO),
                                    @ExampleObject(name = "INSUFFICIENT_AVAILABILITY", value = Ejemplos.ZONA_SIN_LUGAR),
                                    @ExampleObject(name = "EVENT_NOT_ON_SALE", value = Ejemplos.EVENTO_NO_EN_VENTA)
                            })),
            @ApiResponse(responseCode = "422", description = "El cliente supera el límite de entradas del evento",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "CLIENT_LIMIT_EXCEEDED", value = Ejemplos.LIMITE)))
    })
    public ResponseEntity<ReservationResponse> reservar(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ReservaRequest.class),
                            examples = @ExampleObject(value = Ejemplos.RESERVA_PEDIDO)))
            @Valid @RequestBody ReservaRequest request,
            @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true,
                    description = "UUID del cliente. Lo propaga el gateway.",
                    schema = @Schema(type = "string", format = "uuid"), example = Ejemplos.CLIENTE)
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(name = "X-User-Role", in = ParameterIn.HEADER, required = true,
                    description = "CLIENT, ORGANIZER o ADMIN.",
                    schema = @Schema(type = "string"), example = "CLIENT")
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        ReservationResponse response = reservaService.reservar(request, identity.userId());
        return ResponseEntity.created(URI.create("/api/inventory/reservations/" + response.id())).body(response);
    }

    @GetMapping("/reservations/{reservaId}")
    @Operation(summary = "Consultar una reserva propia",
            description = """
                    Solo el dueño. Si la reserva venció, la respuesta es 200 con estado VENCIDA: \
                    410 RESERVATION_EXPIRED se usa al confirmar o pagar, no en esta consulta.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva del cliente",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ReservationResponse.class),
                            examples = @ExampleObject(value = Ejemplos.RESERVA_CREADA))),
            @ApiResponse(responseCode = "401", description = "Falta la identidad o X-User-Id no es un UUID",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "UNAUTHORIZED", value = Ejemplos.NO_AUTENTICADO))),
            @ApiResponse(responseCode = "403", description = "La reserva es de otro cliente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "NOT_RESERVATION_OWNER", value = Ejemplos.AJENA))),
            @ApiResponse(responseCode = "404", description = "No existe la reserva",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "RESERVATION_NOT_FOUND", value = Ejemplos.RESERVA_NO_ENCONTRADA)))
    })
    public ReservationResponse obtener(
            @PathVariable UUID reservaId,
            @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true,
                    schema = @Schema(type = "string", format = "uuid"), example = Ejemplos.CLIENTE)
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(name = "X-User-Role", in = ParameterIn.HEADER, required = true,
                    schema = @Schema(type = "string"), example = "CLIENT")
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        return reservaService.obtener(reservaId, identity.userId());
    }
}
