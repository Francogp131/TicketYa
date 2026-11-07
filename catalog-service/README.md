# catalog-service

Servicio de catálogo de TicketYa: lugares, sectores, asientos, eventos, precios, publicación, búsqueda y estado del evento.

- **Puerto:** 8081
- **Base:** `catalog_db` (PostgreSQL 16, puerto 5433)
- **Responsable:** P1 — ver [`docs/equipo/compa-1.md`](../docs/equipo/compa-1.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 3.

## Endpoints implementados

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/catalog/venues` | ADMIN | Crea un lugar |
| GET | `/api/catalog/venues` | público | Lista lugares paginados |
| GET | `/api/catalog/venues/{venueId}` | público | Detalle de un lugar |
| POST | `/api/catalog/venues/{venueId}/sectors` | ADMIN | Crea un sector y genera sus asientos si es numerado |
| GET | `/api/catalog/venues/{venueId}/sectors` | público | Lista sectores del lugar |
| POST | `/api/catalog/events` | ORGANIZER | Crea un evento en `BORRADOR` |
| PUT | `/api/catalog/events/{eventoId}/prices/{sectorId}` | ORGANIZER | Asigna el precio de un sector |
| POST | `/api/catalog/events/{eventoId}/publish` | ORGANIZER | Publica y envía el snapshot a inventory |
| POST | `/api/catalog/events/{eventoId}/cancel` | ADMIN | Cancela el evento y avisa a orders |
| GET | `/api/catalog/events` | público | Busca eventos publicados o agotados con filtros, orden y paginación |
| GET | `/api/catalog/events/{eventoId}` | público | Detalle con disponibilidad consultada a inventory |
| PATCH | `/internal/events/{eventoId}/availability` | interno | Callback de inventory: `AGOTADO` ↔ `PUBLICADO` |

La búsqueda acepta `tipo`, `organizadorId`, `participante`, `ciudad`, `fechaDesde`, `fechaHasta`, `orden`, `page` y `size`. Los órdenes válidos son `FECHA_ASC` (default), `FECHA_DESC`, `PRECIO_ASC`, `PRECIO_DESC` y `RELEVANCIA`; esta última prioriza eventos `PUBLICADO` y luego la fecha más próxima. Un rango inválido responde `400 INVALID_DATE_RANGE` y un orden desconocido `400 INVALID_SORT`.

Las transiciones de estado permitidas son `BORRADOR → PUBLICADO`, `PUBLICADO ↔ AGOTADO`, `PUBLICADO/AGOTADO → CANCELADO` y `PUBLICADO/AGOTADO → FINALIZADO`. Si orders no responde al cancelar, el evento queda `CANCELADO` con `cancelacionPendiente: true` y el scheduler reintenta el aviso cada minuto. El mismo scheduler pasa a `FINALIZADO` los eventos cuya fecha ya pasó.

La integración con inventory usa timeout corto (`INVENTORY_CONNECT_TIMEOUT_MS`/`INVENTORY_READ_TIMEOUT_MS`) y hasta 3 intentos con backoff ante timeouts o 5xx; los 4xx no se reintentan. La publicación envía un snapshot idempotente por `eventoId + version`, por lo que un reintento o una republicación no duplica inventario. Inventory notifica los cambios de disponibilidad con `PATCH /internal/events/{eventoId}/availability`.

La cancelación avisa a orders con timeout corto (`ORDERS_CONNECT_TIMEOUT_MS`/`ORDERS_READ_TIMEOUT_MS`). Como ese aviso es idempotente, si orders falla o demora el evento queda `CANCELADO` con `cancelacionPendiente: true` y el scheduler reintenta; esto es la base de la que dependen las devoluciones y la cancelación masiva de entradas.

El OpenAPI documenta la identidad (`X-User-Id`, `X-User-Role`), los ejemplos de request y un esquema común `ProblemDetail` para todos los errores 4xx/5xx, con ejemplos de `VALIDATION_ERROR`, `EVENT_NOT_FOUND`, `EVENT_NOT_EDITABLE`, `INVENTORY_UNAVAILABLE` y `ORDERS_UNAVAILABLE` según el caso.

## Modelo

Tablas Flyway en [`src/main/resources/db/migration`](src/main/resources/db/migration):

- `lugar` — nombre, dirección, ciudad, latitud/longitud.
- `sector` — pertenece a un lugar; `NUMERADO` exige filas y asientos por fila.
- `asiento` — se genera al crear un sector numerado (`filas × asientosPorFila`).
- `evento` — estado `BORRADOR/PUBLICADO/AGOTADO/CANCELADO/FINALIZADO`, organizador, límite por cliente y `cancelacion_pendiente` para el reintento del aviso a orders.
- `evento_participante` y `evento_sector_precio` — participantes y precio por sector.

Las migraciones `V3` y `V4` agregan la cancelación pendiente y refuerzan en la base las reglas de `tipo` de evento y capacidad de sectores numerados. El ER validado está en [`docs/modelo-datos.md`](../docs/modelo-datos.md).

Al publicar, el servicio envía a inventory el snapshot del evento (sectores, precios, capacidad y asientos) por `POST /internal/events/{id}/inventory`. Si inventory falla, el evento sigue en `BORRADOR` y se responde `502 INVENTORY_UNAVAILABLE`.

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5433/catalog_db` | Conexión JDBC |
| `DB_USER` / `DB_PASSWORD` | `ticketya` | Credenciales |
| `INVENTORY_URL` | `http://localhost:8082` | Cliente interno hacia inventory |
| `INVENTORY_CONNECT_TIMEOUT_MS` | `2000` | Timeout de conexión contra inventory |
| `INVENTORY_READ_TIMEOUT_MS` | `3000` | Timeout de lectura contra inventory |
| `ORDERS_URL` | `http://localhost:8083` | Cliente interno hacia orders |
| `ORDERS_CONNECT_TIMEOUT_MS` | `1000` | Timeout de conexión contra orders |
| `ORDERS_READ_TIMEOUT_MS` | `2000` | Timeout de lectura contra orders |
| `CATALOG_SCHEDULER_FINALIZACION_MS` | `60000` | Intervalo del scheduler de estados |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d catalog-db
cd catalog-service
mvn test
mvn spring-boot:run
curl http://localhost:8081/actuator/health
```

Swagger UI: <http://localhost:8081/swagger-ui.html> · OpenAPI: <http://localhost:8081/v3/api-docs>

El OpenAPI exportado está en [`../openapi/catalog-openapi.json`](../openapi/catalog-openapi.json). Se regenera ejecutando `mvn -Dtest=OpenApiExportTest test`; el test escribe el archivo generado por springdoc en `openapi/catalog-openapi.json` de la raíz del repositorio.

Los tests unitarios y de controller no necesitan Docker. La validación de integración con PostgreSQL se corre levantando la base y los servicios (ver `docs/pruebas.md`).
