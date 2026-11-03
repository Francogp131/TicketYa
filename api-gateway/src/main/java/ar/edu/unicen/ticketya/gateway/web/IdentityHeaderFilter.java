package ar.edu.unicen.ticketya.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Punto único de identidad de la Entrega 1.
 *
 * <p>Exige X-User-Id (UUID) y X-User-Role en los endpoints protegidos, aplica la regla de rol
 * del contrato, propaga ambos encabezados y agrega X-Correlation-Id. En la Entrega 2 este filtro
 * valida JWT y sigue propagando los mismos encabezados, sin cambiar los servicios.</p>
 */
@Component
public class IdentityHeaderFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private static final Set<String> ROLES = Set.of("CLIENT", "ORGANIZER", "ADMIN");
    private static final Set<String> ADMIN_ONLY = Set.of("ADMIN");
    private static final Set<String> ORGANIZER_OR_ADMIN = Set.of("ORGANIZER", "ADMIN");
    private static final Set<String> AUTHENTICATED = Set.of("CLIENT", "ORGANIZER", "ADMIN");

    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "/api/catalog/events",
            "/api/catalog/venues");

    private static final Pattern PUBLIC_GET_PATTERN = Pattern.compile(
            "^/api/catalog/(events|venues)/[0-9a-fA-F-]{36}$"
                    + "|^/api/inventory/events/[0-9a-fA-F-]{36}/availability$"
                    + "|^/api/inventory/events/[0-9a-fA-F-]{36}/seats$");

    private final ObjectMapper objectMapper;

    public IdentityHeaderFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        boolean publicRead = HttpMethod.GET.equals(method)
                && (PUBLIC_GET_PATHS.contains(path) || PUBLIC_GET_PATTERN.matcher(path).matches());

        String incomingCorrelationId = request.getHeaders().getFirst(CORRELATION_HEADER);
        final String correlationId = (incomingCorrelationId == null || incomingCorrelationId.isBlank())
                ? UUID.randomUUID().toString()
                : incomingCorrelationId;

        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(headers -> headers.set(CORRELATION_HEADER, correlationId));

        if (publicRead) {
            requestBuilder.headers(headers -> {
                headers.remove(USER_ID_HEADER);
                headers.remove(USER_ROLE_HEADER);
            });
            exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        String userId = request.getHeaders().getFirst(USER_ID_HEADER);
        String role = request.getHeaders().getFirst(USER_ROLE_HEADER);

        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Faltan los encabezados X-User-Id y X-User-Role");
        }

        try {
            UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "X-User-Id debe ser un UUID válido");
        }

        String normalizedRole = role.trim().toUpperCase();
        if (!ROLES.contains(normalizedRole)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "X-User-Role debe ser CLIENT, ORGANIZER o ADMIN");
        }

        Set<String> requiredRoles = requiredRoles(path, method);
        if (!requiredRoles.contains(normalizedRole)) {
            return reject(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "El rol " + normalizedRole + " no está autorizado para esta operación");
        }

        requestBuilder.headers(headers -> {
            headers.set(USER_ID_HEADER, userId);
            headers.set(USER_ROLE_HEADER, normalizedRole);
        });
        exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private Set<String> requiredRoles(String path, HttpMethod method) {
        if (path.startsWith("/api/catalog/venues")) {
            return ADMIN_ONLY;
        }
        if (path.startsWith("/api/catalog/events")) {
            if (HttpMethod.POST.equals(method) && path.matches("^/api/catalog/events/[0-9a-fA-F-]{36}/cancel$")) {
                return ADMIN_ONLY;
            }
            if (HttpMethod.POST.equals(method)
                    && (path.equals("/api/catalog/events")
                    || path.matches("^/api/catalog/events/[0-9a-fA-F-]{36}/publish$"))) {
                return ORGANIZER_OR_ADMIN;
            }
            if (HttpMethod.PUT.equals(method)
                    && path.matches("^/api/catalog/events/[0-9a-fA-F-]{36}/prices/[0-9a-fA-F-]{36}$")) {
                return ORGANIZER_OR_ADMIN;
            }
            return AUTHENTICATED;
        }
        if (path.matches("^/api/orders/returns/[0-9a-fA-F-]{36}/(approve|reject)$")) {
            return ADMIN_ONLY;
        }
        return AUTHENTICATED;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://ticketya.local/errors/" + code.toLowerCase()));
        problem.setInstance(URI.create(exchange.getRequest().getURI().getPath()));
        problem.setProperty("code", code);
        problem.setProperty("errors", List.of());

        try {
            byte[] body = objectMapper.writeValueAsBytes(problem);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException ex) {
            byte[] fallback = ("{\"status\":" + status.value() + ",\"code\":\"" + code + "\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
