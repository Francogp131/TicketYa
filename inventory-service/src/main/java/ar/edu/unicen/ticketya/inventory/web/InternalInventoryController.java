package ar.edu.unicen.ticketya.inventory.web;

import ar.edu.unicen.ticketya.inventory.servicio.ConfirmacionService;
import ar.edu.unicen.ticketya.inventory.servicio.InventarioService;
import ar.edu.unicen.ticketya.inventory.servicio.ReservaService;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.InventoryUpsertResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReleaseRequest;
import ar.edu.unicen.ticketya.inventory.web.dto.ReservationResponse;
import ar.edu.unicen.ticketya.inventory.web.dto.ReturnRequest;
import ar.edu.unicen.ticketya.inventory.web.openapi.Ejemplos;
import ar.edu.unicen.ticketya.inventory.web.openapi.Problema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@Tag(name = "Inventario interno", description = "Llamadas de catalog y orders. El gateway no las expone.")
public class InternalInventoryController {

    private final InventarioService inventarioService;
    private final ReservaService reservaService;
    private final ConfirmacionService confirmacionService;

    public InternalInventoryController(InventarioService inventarioService,
                                       ReservaService reservaService,
                                       ConfirmacionService confirmacionService) {
        this.inventarioService = inventarioService;
        this.reservaService = reservaService;
        this.confirmacionService = confirmacionService;
    }

    @PostMapping("/events/{eventoId}/inventory")
    @Operation(summary = "Alta o actualización del inventario publicado",
            description = """
                    Idempotente por eventoId + version. Reintentar la misma clave no duplica filas \
                    ni reinicia la disponibilidad ya vendida.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventario guardado",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InventoryUpsertResponse.class),
                            examples = @ExampleObject(value = Ejemplos.ALTA_RESPUESTA))),
            @ApiResponse(responseCode = "400", description = "Cuerpo inválido",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", value = Ejemplos.VALIDACION)))
    })
    public InventoryUpsertResponse upsert(@PathVariable UUID eventoId,
                                          @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                                                  content = @Content(mediaType = "application/json",
                                                          schema = @Schema(implementation = InventoryUpsertRequest.class),
                                                          examples = @ExampleObject(value = Ejemplos.ALTA_INVENTARIO)))
                                          @Valid @RequestBody InventoryUpsertRequest request) {
        return inventarioService.upsert(eventoId, request);
    }

    /**
     * Consulta que usa orders-service al crear la orden. Incluye nombre y fecha
     * del evento para que orders guarde el snapshot sin depender de catalog.
     */
    @GetMapping("/reservations/{reservaId}")
    @Operation(summary = "Reserva para orders",
            description = "Incluye eventoNombre y eventoFecha para que orders guarde el snapshot sin llamar a catalog.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva, exista o no el dueño",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ReservationResponse.class),
                            examples = @ExampleObject(value = Ejemplos.RESERVA_CREADA))),
            @ApiResponse(responseCode = "404", description = "No existe la reserva",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "RESERVATION_NOT_FOUND", value = Ejemplos.RESERVA_NO_ENCONTRADA)))
    })
    public ReservationResponse obtenerReserva(@PathVariable UUID reservaId) {
        return reservaService.obtenerInterno(reservaId);
    }

    @PostMapping("/reservations/{reservaId}/confirm")
    @Operation(summary = "Confirmar la venta",
            description = "Pasa la reserva de VIGENTE a CONFIRMADA si no venció y los asientos de RESERVADO a VENDIDO. Repetir una confirmada devuelve 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva confirmada, también si ya estaba CONFIRMADA",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe la reserva",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class))),
            @ApiResponse(responseCode = "409", description = "La reserva no se puede confirmar",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class))),
            @ApiResponse(responseCode = "410", description = "La reserva venció",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class),
                            examples = @ExampleObject(name = "RESERVATION_EXPIRED", value = Ejemplos.RESERVA_VENCIDA)))
    })
    public ReservationResponse confirmar(@PathVariable UUID reservaId) {
        return confirmacionService.confirmar(reservaId);
    }

    @PostMapping("/reservations/{reservaId}/release")
    @Operation(summary = "Liberar una reserva",
            description = "Libera la reserva si el pago se rechazó. Idempotente: repetir la llamada devuelve 204 sin reponer dos veces.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reserva liberada, o ya lo estaba"),
            @ApiResponse(responseCode = "404", description = "No existe la reserva",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class)))
    })
    public ResponseEntity<Void> liberar(@PathVariable UUID reservaId,
                                        @RequestBody(required = false) ReleaseRequest request) {
        confirmacionService.liberar(reservaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/returns")
    @Operation(summary = "Devolver lugares",
            description = "Devuelve un asiento vendido o repone una zona. Idempotente con Idempotency-Key.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lugares devueltos"),
            @ApiResponse(responseCode = "404", description = "No existe el sector",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = Problema.class)))
    })
    public ResponseEntity<Void> devolver(
            @RequestHeader(value = "Idempotency-Key", required = false) String clave,
            @Valid @RequestBody ReturnRequest request) {
        confirmacionService.devolver(clave, request);
        return ResponseEntity.noContent().build();
    }
}
