# P1 — Catalog (Iván)

**Servicio:** `catalog-service` (puerto **8081**, base `catalog_db` en **5433**)
**Rama base:** `main`
**Tareas OpenSpec:** 2.1 a 2.6
**Estado al 4/11:** 2.1 y 2.2 cerradas en el PR #1. Arrancás con **2.3 (estados)** y **2.5 (búsqueda)**.

## 1. Antes de empezar

1. Leer [`../api-contract.md`](../api-contract.md) (sección 3 y catálogo de errores).
2. Leer `openspec/changes/entrega-1-core/specs/gestion-eventos-lugares/spec.md` y `specs/busqueda-eventos/spec.md`.
3. Leer `openspec/changes/entrega-1-core/design.md` (decisiones 1, 4, 5 y 6).
4. Levantar la base:

```bash
docker compose -f docker-compose.dev.yml up -d catalog-db
cd catalog-service
mvn test
mvn spring-boot:run
curl http://localhost:8081/actuator/health
```

Swagger: <http://localhost:8081/swagger-ui.html>

## 2. Alcance

Sos dueño de todo lo que está dentro de `catalog-service/`:

- Modelo JPA: `Lugar`, `Sector`, `Asiento`, `Evento`, `EventoParticipante`, `EventoSectorPrecio`.
- Repositorios, servicios, controllers, DTOs y validaciones.
- OpenAPI y README del servicio.
- Cliente REST hacia inventory (publicación y disponibilidad).
- Scheduler de finalización.

**No toques** `inventory-service/`, `orders-service/` ni `api-gateway/`. Si necesitás un cambio de contrato, se acuerda y se actualiza `docs/api-contract.md` en un PR aparte.

## 3. Tareas y criterios de aceptación

### 2.1 Lugares, sectores y asientos (US-01 a US-03)

Endpoints:

- `POST /api/catalog/venues` (ADMIN)
- `GET /api/catalog/venues` y `GET /api/catalog/venues/{id}` (público)
- `POST /api/catalog/venues/{id}/sectors` (ADMIN)
- `GET /api/catalog/venues/{id}/sectors` (público)

Criterios:

- Crear lugar valida nombre/dirección no vacíos y latitud/longitud en rango → 400 con `VALIDATION_ERROR`.
- Rol distinto de ADMIN → 403 (lo aplica el gateway; el servicio también puede validar).
- Sector `NUMERADO` con `filas` y `asientosPorFila` genera exactamente `filas * asientosPorFila` asientos con `(fila, numero)` únicos.
- Sector `NO_NUMERADO` con capacidad ≤ 0 → 400.
- Lugar inexistente → 404 `VENUE_NOT_FOUND`.
- Tests: unitarios de validación + integración con PostgreSQL (Testcontainers) para la generación de asientos.

### 2.2 Eventos, precios y publicación (US-04 a US-06)

Endpoints:

- `POST /api/catalog/events` (ORGANIZER)
- `PUT /api/catalog/events/{eventoId}/prices/{sectorId}` (ORGANIZER)
- `POST /api/catalog/events/{eventoId}/publish` (ORGANIZER)
- `GET /api/catalog/events/{eventoId}` (público)

Criterios:

- Evento nuevo nace `BORRADOR` y con `organizadorId` tomado de `X-User-Id`.
- Fecha pasada, tipo fuera del enum o límite ≤ 0 → 400.
- Sector que no pertenece al lugar del evento → 400 `SECTOR_NOT_FROM_VENUE`.
- Precio negativo → 400 `INVALID_PRICE`.
- Evento de otro organizador → 403 `NOT_EVENT_OWNER`.
- Evento ya publicado y se intenta cambiar precio → 409 `EVENT_NOT_EDITABLE`.
- Publicar sin precios de todos los sectores → 409 `EVENT_INCOMPLETE` con detalle de los sectores faltantes.
- Publicar con inventory caído → 502 `INVENTORY_UNAVAILABLE` y el evento queda `BORRADOR`.
- Tests: todos los casos anteriores + republicar el mismo evento no duplica inventario.

### 2.3 Estados y scheduler (US-07, US-08)

- Transiciones válidas: `BORRADOR → PUBLICADO`, `PUBLICADO ↔ AGOTADO`, `PUBLICADO/AGOTADO → CANCELADO`, `PUBLICADO/AGOTADO → FINALIZADO`.
- Transición inválida → 409 `EVENT_NOT_EDITABLE` o `EVENT_NOT_CANCELLABLE`.
- `POST /api/catalog/events/{id}/cancel` (ADMIN) pasa a `CANCELADO` y llama a orders; si orders falla, responde 200 con `cancelacionPendiente: true` y un scheduler reintenta.
- Scheduler cada minuto pasa a `FINALIZADO` los eventos publicados cuya fecha ya pasó.
- Tests con reloj fijo (`Clock`) para no depender del tiempo real.

### 2.4 Integración con inventory (US-06, US-08)

- Cliente REST `InventoryClient` con timeout corto y reintentos (máximo 3, backoff).
- `POST /internal/events/{eventoId}/inventory` con snapshot completo y `version`; idempotente por `eventoId + version`.
- `PATCH /internal/events/{eventoId}/availability` desde inventory para `AGOTADO`/`PUBLICADO`.
- Tests con WireMock o Testcontainers: publicación exitosa, inventory 5xx, reintento y republicación sin duplicar.

### 2.5 Búsqueda y detalle (US-09 a US-11)

- `GET /api/catalog/events` con filtros combinables (`tipo`, `organizadorId`, `participante`, `ciudad`, `fechaDesde`, `fechaHasta`), orden y paginación.
- Solo eventos `PUBLICADO` o `AGOTADO`.
- Lista vacía → 200 con `content: []`.
- `fechaDesde > fechaHasta` → 400 `INVALID_DATE_RANGE`; orden inválido → 400 `INVALID_SORT`.
- `GET /api/catalog/events/{id}` devuelve sectores, precios y disponibilidad consultada a inventory.
- Evento `BORRADOR` → 404.
- Tests de filtros combinados, orden, total de páginas y detalle con inventory caído (503 `INVENTORY_UNAVAILABLE`).

### 2.6 OpenAPI y README

- Anotaciones `@Operation`, `@ApiResponse` y ejemplos en los DTOs.
- Swagger UI muestra todos los endpoints de la sección 3 del contrato.
- `openapi.json` exportado en `openapi/catalog-openapi.json`.
- README del servicio con responsabilidad, endpoints, modelo, variables y comandos de test.

## 4. Modelo de datos (ya migrado en `V1__init.sql`)

Tablas: `lugar`, `sector`, `asiento`, `evento`, `evento_participante`, `evento_sector_precio`.

- `asiento` se genera al crear un sector numerado.
- `evento_sector_precio` es único por `(evento_id, sector_id)`.
- `evento.estado` tiene check con los cinco estados.
- `evento.fecha` es `TIMESTAMPTZ`; en Java usar `OffsetDateTime` o `Instant` y serializar con offset de Argentina.

Si necesitás un índice o columna nueva, creá `V2__*.sql`; no edites `V1`.

## 5. Ramas sugeridas

| Rama | Tarea |
|---|---|
| `feat/catalog-lugares-sectores` | 2.1 |
| `feat/catalog-eventos-precios` | 2.2 |
| `feat/catalog-estados-scheduler` | 2.3 |
| `feat/catalog-integracion-inventory` | 2.4 |
| `feat/catalog-busqueda-detalle` | 2.5 |
| `docs/catalog-openapi` | 2.6 |

## 6. PR

Título: `feat(catalog): <tema> (tarea 2.x)`

En el cuerpo:

- Qué endpoints/tablas toca.
- Tarea de `tasks.md` marcada.
- Salida de `mvn test`.
- Ejemplos de `curl` de los casos felices y de error.
- Confirmación de que `docs/api-contract.md` y el OpenAPI están actualizados.

## 7. Coordinación

- **Con P2 (inventory):** formato exacto del snapshot de publicación y del callback de disponibilidad. Si cambia, se actualiza el contrato y ambos PRs.
- **Con P3 (orders):** el detalle del evento incluye precios; orders guarda su propio snapshot.
- **Con P4 (gateway):** cualquier ruta nueva debe estar en las reglas de rol del gateway.
- **Con P5 (calidad):** avisá cuando un endpoint esté listo para agregarlo a Postman y a la prueba E2E.

## 8. Terminado cuando

- [ ] 2.1 a 2.6 marcadas en `tasks.md`.
- [ ] `mvn test` verde en `catalog-service`.
- [ ] Swagger UI y `openapi/catalog-openapi.json` completos.
- [ ] README del servicio actualizado.
- [ ] CI en verde y PR aprobado por otro integrante.
