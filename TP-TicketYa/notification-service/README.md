# notification-service

Servicio de notificaciones de TicketYa con **PostgreSQL** y **RabbitMQ**.

- **Puerto:** 8085
- **Base:** `notification_db` (PostgreSQL 16, puerto 5436)
- **Broker:** RabbitMQ 3.13 (puerto 5672, consola 15672)
- **Responsable:** P1 — ver [`docs/equipo/entrega-2/p1-ivan.md`](../docs/equipo/entrega-2/p1-ivan.md)
- **Estado:** Día 1 — tarea 1.8 (esqueleto). La tabla `notificacion`, los consumidores y la consulta se agregan en las tareas 8.1–8.4.

## Alcance actual

Este esqueleto incluye Spring Boot 3.5, Java 21, Web, Validation, Actuator, springdoc, Spring Data JPA, Spring AMQP, Flyway y PostgreSQL.

- `GET /actuator/health` reporta el estado de PostgreSQL (`db`) y RabbitMQ (`rabbit`).
- Swagger UI: <http://localhost:8085/swagger-ui.html> · OpenAPI: <http://localhost:8085/v3/api-docs>
- Los errores de futuros endpoints usan RFC 7807 con `code` y `errors[]`.
- La migración `V1__init.sql` solo inicializa Flyway; la tabla de notificaciones corresponde a la tarea 8.1.

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8085` | Puerto HTTP |
| `NOTIFICATION_DB_URL` | `jdbc:postgresql://localhost:5436/notification_db` | Conexión PostgreSQL |
| `DB_USER` | `ticketya` | Usuario de base |
| `DB_PASSWORD` | `ticketya` | Password de base |
| `RABBITMQ_HOST` | `localhost` | Host de RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto AMQP |
| `RABBITMQ_USER` | `ticketya` | Usuario de RabbitMQ |
| `RABBITMQ_PASSWORD` | `ticketya` | Password de RabbitMQ |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d notification-db rabbitmq
cd notification-service
mvn test
mvn spring-boot:run
```

Verificar:

```bash
curl http://localhost:8085/actuator/health
```

La respuesta debe incluir `"status":"UP"` y los componentes `db` y `rabbit` en `UP`.

### Tests

- `NotificationServiceApplicationTests` (Testcontainers PostgreSQL + RabbitMQ): arranca el contexto y verifica que `/actuator/health` reporte base y broker conectados. Se omite si Docker no está corriendo.
