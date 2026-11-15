package ar.edu.unicen.ticketya.notification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketYa - notification-service")
                        .version("1.0.0")
                        .description("""
                                Notificaciones de TicketYa persistidas en PostgreSQL y alimentadas por RabbitMQ.
                                Los endpoints públicos se consumen a través del gateway en /api/notifications/**.
                                La identidad llega como X-User-Id y X-User-Role; el cliente usa JWT contra el gateway.
                                Todos los errores usan RFC 7807 (application/problem+json) con code y errors[].
                                """)
                        .license(new License().name("Uso académico - UNICEN")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT emitido por POST /api/auth/login del gateway"))
                        .addSecuritySchemes("X-User-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("UUID del usuario autenticado, propagado por el gateway"))
                        .addSecuritySchemes("X-User-Role", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Role")
                                .description("Rol del usuario: CLIENT, ORGANIZER, ADMIN o ACCESS_STAFF")));
    }
}
