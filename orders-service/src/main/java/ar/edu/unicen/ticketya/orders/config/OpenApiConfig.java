package ar.edu.unicen.ticketya.orders.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ordersOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TicketYa - orders-service")
                .version("1.0.0")
                .description("""
                        Órdenes, pago mock, entradas con QR, devoluciones y cancelación de evento.
                        Los endpoints públicos se consumen a través del gateway en /api/orders/**.
                        Los errores usan RFC 7807 (application/problem+json) con los campos code y errors[].
                        """)
                .license(new License().name("Uso académico - UNICEN")));
    }
}
