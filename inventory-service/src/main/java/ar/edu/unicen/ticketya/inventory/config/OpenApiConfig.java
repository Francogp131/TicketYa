package ar.edu.unicen.ticketya.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String SERVER = "http://localhost:8082";

    @Bean
    OpenAPI inventoryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("inventory-service")
                        .version("1.0.0")
                        .description("""
                                Disponibilidad, reservas temporales y no doble venta de TicketYa.
                                Los errores usan application/problem+json (RFC 7807) con `code` y `errors`.
                                Códigos de esta API: 200, 201, 204, 400, 401, 403, 404, 409, 410 y 422.
                                """))
                .servers(List.of(new Server().url(SERVER).description("inventory-service")));
    }

    /**
     * Confirmar, liberar y devolver están en el contrato (secciones 4.8 a 4.10) y devuelven 410.
     * Si el controller de la tarea 3.6 ya registró la ruta, no se pisa.
     */
    @Bean
    OpenApiCustomizer contratoDeConfirmacion() {
        return openApi -> {
            openApi.setServers(List.of(new Server().url(SERVER).description("inventory-service")));
            Paths paths = openApi.getPaths();
            if (paths == null) {
                paths = new Paths();
                openApi.setPaths(paths);
            }
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            agregarSiFalta(paths, "/internal/reservations/{reservaId}/confirm", confirmar());
            agregarSiFalta(paths, "/internal/reservations/{reservaId}/release", liberar());
            agregarSiFalta(paths, "/internal/returns", devolver());
        };
    }

    private static void agregarSiFalta(Paths paths, String path, PathItem item) {
        if (paths.get(path) == null) {
            paths.addPathItem(path, item);
        }
    }

    private static PathItem confirmar() {
        Operation operacion = new Operation()
                .operationId("confirmarReserva")
                .tags(List.of("Inventario interno"))
                .summary("Confirmar la venta")
                .description("""
                        Contrato 4.8. Pasa la reserva de VIGENTE a CONFIRMADA solo si `expires_at` es posterior a ahora \
                        y los asientos de RESERVADO a VENDIDO. Repetir una reserva ya CONFIRMADA responde 200 \
                        con el mismo cuerpo. Si venció, responde 410 RESERVATION_EXPIRED.
                        """)
                .addParametersItem(reservaId())
                .responses(new ApiResponses()
                        .addApiResponse("200", json("Reserva confirmada. También si ya estaba CONFIRMADA.",
                                "#/components/schemas/ReservationResponse"))
                        .addApiResponse("404", problema("No existe la reserva.", "RESERVATION_NOT_FOUND"))
                        .addApiResponse("409", problema(
                                "La reserva no está VIGENTE ni CONFIRMADA.", "RESERVATION_NOT_CONFIRMABLE"))
                        .addApiResponse("410", problema(
                                "La reserva venció. No se confirma la venta.", "RESERVATION_EXPIRED")));
        return new PathItem().post(operacion);
    }

    private static PathItem liberar() {
        Operation operacion = new Operation()
                .operationId("liberarReserva")
                .tags(List.of("Inventario interno"))
                .summary("Liberar una reserva vigente")
                .description("""
                        Contrato 4.9. La usa orders cuando el pago se rechaza. \
                        Repetir la llamada sobre una reserva ya liberada responde 204 sin devolver el lugar dos veces.
                        """)
                .addParametersItem(reservaId())
                .requestBody(new RequestBody()
                        .required(false)
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().type("object").addProperty("motivo",
                                        new StringSchema().example("PAGO_RECHAZADO"))))))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Reserva liberada, o ya lo estaba."))
                        .addApiResponse("404", problema("No existe la reserva.", "RESERVATION_NOT_FOUND")));
        return new PathItem().post(operacion);
    }

    private static PathItem devolver() {
        Schema<?> item = new Schema<>().type("object")
                .addProperty("sectorId", new StringSchema().format("uuid"))
                .addProperty("asientoId", new StringSchema().format("uuid").nullable(true))
                .addProperty("cantidad", new Schema<>().type("integer").minimum(java.math.BigDecimal.ONE));
        Schema<?> cuerpo = new Schema<>().type("object")
                .addProperty("eventoId", new StringSchema().format("uuid"))
                .addProperty("items", new Schema<>().type("array").items(item));
        Operation operacion = new Operation()
                .operationId("devolverLugares")
                .tags(List.of("Inventario interno"))
                .summary("Devolver asientos vendidos o reponer una zona")
                .description("""
                        Contrato 4.10. Con el header Idempotency-Key, repetir la misma devolución no repone dos veces. \
                        Sin esa clave, la devolución de un asiento igual es idempotente porque solo pasa de VENDIDO a LIBRE.
                        """)
                .addParametersItem(new Parameter()
                        .name("Idempotency-Key")
                        .in("header")
                        .required(false)
                        .schema(new StringSchema())
                        .example("devolucion-asiento-1")
                        .description("Clave para no reponer una zona dos veces."))
                .requestBody(new RequestBody().required(true)
                        .content(new Content().addMediaType("application/json", new MediaType().schema(cuerpo))))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Lugares devueltos."))
                        .addApiResponse("404", problema("No existe el evento o el sector.", "SECTOR_NOT_FOUND")));
        return new PathItem().post(operacion);
    }

    private static Parameter reservaId() {
        return new Parameter()
                .name("reservaId")
                .in("path")
                .required(true)
                .description("Identificador de la reserva.")
                .schema(new StringSchema().format("uuid"))
                .example("b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e");
    }

    private static ApiResponse json(String descripcion, String ref) {
        return new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref(ref))));
    }

    private static ApiResponse problema(String descripcion, String code) {
        return new ApiResponse()
                .description(descripcion + " Código `" + code + "`.")
                .content(new Content().addMediaType("application/problem+json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/Problema"))
                        .addExamples(code, new Example().summary(code).value(java.util.Map.of(
                                "type", "https://ticketya.local/errors/" + code.toLowerCase().replace('_', '-'),
                                "title", titulo(code),
                                "status", status(code),
                                "detail", descripcion,
                                "instance", "/internal/reservations/b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
                                "code", code,
                                "errors", List.of())))));
    }

    private static String titulo(String code) {
        return switch (code) {
            case "RESERVATION_EXPIRED" -> "Gone";
            case "RESERVATION_NOT_FOUND", "SECTOR_NOT_FOUND" -> "Not Found";
            default -> "Conflict";
        };
    }

    private static int status(String code) {
        return switch (code) {
            case "RESERVATION_EXPIRED" -> 410;
            case "RESERVATION_NOT_FOUND", "SECTOR_NOT_FOUND" -> 404;
            default -> 409;
        };
    }
}
