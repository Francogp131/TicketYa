# notification-service

Servicio de notificaciones de TicketYa con **PostgreSQL** y **RabbitMQ**.

- **Puerto:** 8085
- **Base:** `notification_db` (PostgreSQL 16, puerto 5436)
- **Broker:** RabbitMQ 3.13 (puerto 5672, consola 15672)
- **Responsable:** P1 — ver [`docs/equipo/entrega-2/p1-ivan.md`](../docs/equipo/entrega-2/p1-ivan.md)
- **Estado:** avance Día 4 — tarea 8.2 (consumidor de `orden.pagada` con idempotencia, reintentos y DLQ). Los consumidores de reintegros/devoluciones y la consulta se agregan en 8.3–8.4.

## Alcance actual

Este esqueleto incluye Spring Boot 3.5, Java 21, Web, Validation, Actuator, springdoc, Spring Data JPA, Spring AMQP, Flyway y PostgreSQL.

- `GET /actuator/health` reporta el estado de PostgreSQL (`db`) y RabbitMQ (`rabbit`).
- Swagger UI: <http://localhost:8085/swagger-ui.html> — OpenAPI: <http://localhost:8085/v3/api-docs>
- Los errores de futuros endpoints usan RFC 7807 con `code` y `errors[]`.
- Migraciones Flyway: `V1__init.sql` y `V2__notificacion.sql` (tabla `notificacion`, estados, idempotencia por `event_id+tipo+destinatario_id`).

### Consumo de `orden.pagada` (tarea 8.2)

- Cola durable `notification.order` enlazada al exchange topic `ticketya.events` con routing key `orden.pagada`; DLX `ticketya.events.dlx` y DLQ `notification.order.dlq`.
- `OrdenPagadaListener` valida el sobre y el payload (`estado=PAGADA`), arma la notificación y delega en `NotificationService`, que deduplica por `eventId+tipo+destinatarioId` y aplica los reintentos de envío.
- Errores transitorios (por ejemplo PostgreSQL caído) se reintentan con backoff (3 intentos) y luego se rechazan sin reencolar hacia `notification.order.dlq`; los mensajes inválidos se rechazan y también terminan en la DLQ.
- El payload puede incluir `clienteEmail` de forma opcional. Si falta, el sender mock usa `cliente-<uuid>@ticketya.local` para poder registrar el envío en desarrollo; un sender real requiere el email.

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
| `NOTIFICATION_MAX_ATTEMPTS` | `3` | Intentos máximos de envío |
| `NOTIFICATION_BACKOFF_SECONDS` | `30` | Base de backoff entre reintentos |
| `NOTIFICATION_QUEUE` | `notification.order` | Cola de consumo de eventos de compra |
| `NOTIFICATION_QUEUE_DLQ` | `notification.order.dlq` | Cola de mensajes rechazados |
| `NOTIFICATION_CONSUMER_MAX_ATTEMPTS` | `3` | Reintentos del listener antes de la DLQ |
| `NOTIFICATION_MOCK_EMAIL_DOMAIN` | `ticketya.local` | Dominio del email sintético del sender mock |

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
- `NotificationServiceTest`: envío exitoso/fallido, reintento, fallo definitivo e idempotencia por evento+tipo+destinatario.
- `OrdenPagadaEventContractTest`: deserializa el fixture compartido `docs/ejemplos/orden-pagada.json`.
- `OrdenPagadaListenerTest`: mapeo a notificación, email del payload/fallback mock y rechazo a DLQ de mensajes inválidos.
- `RabbitConfigTest`: cola durable con DLX/DLQ y bindings de `notification.order`.
