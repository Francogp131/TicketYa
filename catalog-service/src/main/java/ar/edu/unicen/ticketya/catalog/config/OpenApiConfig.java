package ar.edu.unicen.ticketya.catalog.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";

    @Bean
    public OpenAPI catalogOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketYa - catalog-service")
                        .version("1.0.0")
                        .description("""
                                Catálogo de lugares, sectores, asientos, eventos, precios, publicación, estados y búsqueda.
                                Los endpoints públicos se consumen a través del gateway en /api/catalog/**.
                                La identidad se propaga con los headers X-User-Id y X-User-Role.
                                Todos los errores usan RFC 7807 (application/problem+json) con los campos code y errors[].
                                """)
                        .license(new License().name("Uso académico - UNICEN")))
                .components(new Components()
                        .addSecuritySchemes("X-User-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("UUID del usuario autenticado en el gateway"))
                        .addSecuritySchemes("X-User-Role", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Role")
                                .description("Rol del usuario: ADMIN, ORGANIZER o CUSTOMER")));
    }

    @Bean
    public OpenApiCustomizer catalogErrorResponsesCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents() != null
                    ? openApi.getComponents()
                    : new Components();
            openApi.setComponents(components);
            registrarEsquemaDeError(components);
            registrarErroresComunes(components);
            openApi.getPaths().values().forEach(pathItem -> {
                List<io.swagger.v3.oas.models.Operation> operations = pathItem.readOperations();
                operations.forEach(operation -> enriquecerRespuestas(operation, components));
            });
        };
    }

    private void registrarEsquemaDeError(Components components) {
        ObjectSchema problem = new ObjectSchema();
        problem.setDescription("Error RFC 7807 con código de negocio y detalle por campo");
        problem.addProperty("type", new StringSchema().example("about:blank"));
        problem.addProperty("title", new StringSchema().example("Bad Request"));
        problem.addProperty("status", new IntegerSchema().example(400));
        problem.addProperty("detail", new StringSchema().example("Uno o más campos son inválidos"));
        problem.addProperty("instance", new StringSchema().example("/api/catalog/events"));
        problem.addProperty("code", new StringSchema().example("VALIDATION_ERROR"));
        problem.addProperty("errors", new ArraySchema().items(new StringSchema())
                .example("nombre: must not be blank"));
        components.addSchemas("ProblemDetail", problem);
    }

    private void registrarErroresComunes(Components components) {
        components.addResponses("BadRequest", errorResponse(400, "Bad Request",
                "Solicitud inválida", "VALIDATION_ERROR",
                "Uno o más campos son inválidos", List.of("nombre: must not be blank")));
        components.addResponses("Unauthorized", errorResponse(401, "Unauthorized",
                "Falta identidad", "UNAUTHORIZED",
                "El gateway no propagó X-User-Id o X-User-Role", List.of()));
        components.addResponses("Forbidden", errorResponse(403, "Forbidden",
                "Permiso insuficiente", "FORBIDDEN",
                "El rol del usuario no permite esta operación", List.of()));
        components.addResponses("NotFound", errorResponse(404, "Not Found",
                "Recurso inexistente", "EVENT_NOT_FOUND",
                "No existe un evento con el identificador indicado", List.of()));
        components.addResponses("Conflict", errorResponse(409, "Conflict",
                "Conflicto de estado", "EVENT_NOT_EDITABLE",
                "El evento ya no se encuentra en un estado editable", List.of()));
        components.addResponses("UnprocessableEntity", errorResponse(422, "Unprocessable Entity",
                "Regla de negocio incumplida", "CLIENT_LIMIT_EXCEEDED",
                "El cliente superó el límite de entradas configurado", List.of()));
        components.addResponses("BadGateway", errorResponse(502, "Bad Gateway",
                "Servicio interno no disponible", "INVENTORY_UNAVAILABLE",
                "inventory no confirmó la operación", List.of()));
        components.addResponses("ServiceUnavailable", errorResponse(503, "Service Unavailable",
                "Servicio interno no disponible", "INVENTORY_UNAVAILABLE",
                "No se pudo consultar la disponibilidad en inventory", List.of()));
        components.addResponses("InternalServerError", errorResponse(500, "Internal Server Error",
                "Error inesperado", "INTERNAL_ERROR",
                "Ocurrió un error no controlado en catalog-service", List.of()));
    }

    private ApiResponse errorResponse(int status, String title, String detail, String code,
                                      String exampleDetail, List<String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", exampleDetail);
        body.put("instance", "/api/catalog/events");
        body.put("code", code);
        body.put("errors", errors);
        Example example = new Example().summary(detail).value(body);
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref(PROBLEM_REF))
                .addExamples(code, example);
        return new ApiResponse()
                .description(detail)
                .content(new Content().addMediaType(PROBLEM_JSON, mediaType));
    }

    private void enriquecerRespuestas(io.swagger.v3.oas.models.Operation operation,
                                      Components components) {
        ApiResponses responses = operation.getResponses() != null
                ? operation.getResponses()
                : new ApiResponses();
        operation.setResponses(responses);
        responses.putIfAbsent("400", components.getResponses().get("BadRequest"));
        responses.putIfAbsent("500", components.getResponses().get("InternalServerError"));
        responses.forEach((status, response) -> {
            if (status.startsWith("4") || status.startsWith("5")) {
                ApiResponse component = components.getResponses().get(nombreDeError(status));
                if (component != null) {
                    response.setContent(component.getContent());
                }
            }
        });
    }

    private String nombreDeError(String status) {
        return switch (status) {
            case "400" -> "BadRequest";
            case "401" -> "Unauthorized";
            case "403" -> "Forbidden";
            case "404" -> "NotFound";
            case "409" -> "Conflict";
            case "422" -> "UnprocessableEntity";
            case "502" -> "BadGateway";
            case "503" -> "ServiceUnavailable";
            case "500" -> "InternalServerError";
            default -> "InternalServerError";
        };
    }
}
