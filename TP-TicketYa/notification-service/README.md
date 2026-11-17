# notification-service

Servicio de notificaciones de TicketYa con **PostgreSQL** y **RabbitMQ**.

- **Puerto:** 8085
- **Base:** `notification_db` (PostgreSQL 16, puerto 5436)
- **Broker:** RabbitMQ 3.13 (puerto 5672, consola 15672)
- **Responsable:** P1 — ver [`docs/equipo/entrega-2/p1-ivan.md`](../docs/equipo/entrega-2/p1-ivan.md)
- **Estado:** avance Día 4 — tareas 8.2, 8.3 y 8.4 (compra, cancelación, cambio, artista favorito y consulta de notificaciones propias).

## Alcance actual

Este esqueleto incluye Spring Boot 3.5, Java 21, Web, Validation, Actuator, springdoc, Spring Data JPA, Spring AMQP, Flyway y PostgreSQL.

- `GET /actuator/health` reporta el estado de PostgreSQL (`db`) y RabbitMQ (`rabbit`).
- Swagger UI: <http://localhost:8085/swagger-ui.html> — OpenAPI: <http://localhost:8085/v3/api-docs>
- Los errores de futuros endpoints usan RFC 7807 con `code` y `errors[]`.
- Migraciones Flyway: `V1__init.sql`, `V2__notificacion.sql` (tabla `notificacion`, estados, idempotencia por `event_id+tipo+destinatario_id`) y `V3__comprador_evento.sql` (proyección de compradores para avisos de evento).

### Consumo de `orden.pagada` (tarea 8.2)

- Cola durable `notification.order` enlazada al exchange topic `ticketya.events` con routing key `orden.pagada`; DLX `ticketya.events.dlx` y DLQ `notification.order.dlq`.
- `OrdenPagadaListener` valida el sobre y el payload (`estado=PAGADA`), arma la notificación y delega en `NotificationService`, que deduplica por `eventId+tipo+destinatarioId` y aplica los reintentos de envío.
- Errores transitorios (por ejemplo PostgreSQL caído) se reintentan con backoff (3 intentos) y luego se rechazan sin reencolar hacia `notification.order.dlq`; los mensajes inválidos se rechazan y también terminan en la DLQ.
- El payload puede incluir `clienteEmail` de forma opcional. Si falta, el sender mock usa `cliente-<uuid>@ticketya.local` para poder registrar el envío en desarrollo; un sender real requiere el email.

### Avisos de cancelación, cambio y artista favorito (tarea 8.3)

- `orden.pagada` también alimenta la proyección local `comprador_evento` (evento, cliente, email y snapshot de nombre/fecha/ciudad).
- `notification.event` recibe `evento.cancelado` y `evento.actualizado`; se avisa a cada comprador de la proyección con idempotencia por `eventId+tipo+destinatario` y el cambio incluye el dato anterior y el nuevo.
- `notification.favorite` recibe `evento.publicado`; notification-service consulta `GET /internal/preferences?artista=&ciudad=` por cada artista y deduplica clientes.
- Si recommendation-service no responde o el endpoint de búsqueda todavía no existe, se registra un warning y no se generan avisos de favoritos: la publicación del evento no se bloquea. El contrato propuesto está en `docs/api-contract.md` §6.3.1.
- La proyección solo conoce compras consumidas desde este servicio; no reemplaza un historial previo de orders.

### Consulta de notificaciones propias (tarea 8.4)

- `GET /api/notifications?page=0&size=20&estado=PENDIENTE` devuelve solo las notificaciones del `X-User-Id`, ordenadas por fecha de creación descendente, con estado, intentos y fechas de envío.
- `GET /api/notifications/{id}` devuelve una notificación propia; la de otro cliente responde `403 NOTIFICATION_FORBIDDEN` y una inexistente `404 NOTIFICATION_NOT_FOUND`.
- Solo el rol `CLIENT` puede consultar; sin identidad responde `401` y con otro rol `403`. `size` se recorta a 100 y el filtro de estado inválido responde `400 VALIDATION_ERROR`.
- El contrato está en `docs/api-contract.md` §7 y la descripción de la API se publica en `/v3/api-docs`.

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
| `NOTIFICATION_EVENT_QUEUE` | `notification.event` | Cola de cancelaciones y cambios |
| `NOTIFICATION_EVENT_DLQ` | `notification.event.dlq` | DLQ de cancelaciones y cambios |
| `NOTIFICATION_FAVORITE_QUEUE` | `notification.favorite` | Cola de eventos publicados |
| `NOTIFICATION_FAVORITE_DLQ` | `notification.favorite.dlq` | DLQ de eventos publicados |
| `RECOMMENDATION_URL` | `http://localhost:8084` | Base URL para búsqueda de preferencias |
| `PREFERENCES_CONNECT_TIMEOUT` | `2s` | Timeout de conexión a preferencias |
| `PREFERENCES_READ_TIMEOUT` | `3s` | Timeout de lectura de preferencias |
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
- `RabbitConfigTest`: colas durables con DLX/DLQ y bindings de `notification.order`, `notification.event` y `notification.favorite`.
- `CompradorEventoServiceTest`: proyección de compradores y snapshot del evento.
- `EventoNotificacionListenerTest`: cancelación/cambio a todos los compradores, sin compradores y mensajes inválidos.
- `EventoPublicadoListenerTest`: deduplicación por cliente entre artistas y no coincidencia.
- `PreferenceClientTest`: búsqueda interna con token y degradación a lista vacía ante 5xx.
- `ConsultaNotificacionServiceTest`: solo propias, orden por fecha descendente, filtro por estado, recorte de tamaño y 403/404 del detalle.
- `NotificacionControllerTest`: 401, 403 por rol o notificación ajena, 200 con estado/intentos/paginación y 400 por estado inválido.
