# P2 — Inventory (Pablo)

**Servicio:** `inventory-service` (puerto **8082**, base `inventory_db` en **5434**)
**Rama base:** `main`
**Tareas OpenSpec:** 3.1 a 3.8
**Estado al 4/11:** 3.1 y 3.2 cerradas en el PR #1; la reserva de zonas (3.3) ya está implementada y solo falta ampliar tests. Arrancás con **3.4 (límite)** y **3.5 (vencimiento)**.

## 1. Antes de empezar

1. Leer [`../api-contract.md`](../api-contract.md) (sección 4 y catálogo de errores).
2. Leer `openspec/changes/entrega-1-core/specs/reserva-y-compra/spec.md`.
3. Leer `design.md`, decisiones 4 y 6 (modelo replicado, reserva atómica y no doble venta).
4. Levantar la base:

```bash
docker compose -f docker-compose.dev.yml up -d inventory-db
cd inventory-service
mvn test
mvn spring-boot:run
curl http://localhost:8082/actuator/health
```

Swagger: <http://localhost:8082/swagger-ui.html>

## 2. Alcance

Sos dueño de todo lo que está dentro de `inventory-service/`:

- Modelo JPA: `EventoInventario`, `SectorInventario`, `AsientoInventario`, `Reserva`, `ReservaItem`, `ClienteEvento`.
- Repositorios con queries atómicas (`FOR UPDATE`, `UPDATE ... RETURNING`, advisory locks).
- Servicio de reservas, confirmación, liberación y vencimiento.
- Scheduler de reservas vencidas.
- Endpoints públicos, internos y su OpenAPI.
- La prueba de concurrencia (3.7), que es **la más importante de la entrega**.

**No toques** catalog, orders ni gateway.

## 3. Tareas y criterios de aceptación

### 3.1 Modelo y alta de inventario (US-06)

Endpoint interno: `POST /internal/events/{eventoId}/inventory`.

- Recibe el snapshot de catalog: evento, fecha, límite, sectores y asientos.
- Upsert idempotente por `eventoId + version`: dos llamadas con la misma clave no duplican filas ni reinician disponibilidad vendida.
- Inserta `evento_inventario`, `sector_inventario` y `asiento_inventario` en una sola transacción.
- Tests de integración con PostgreSQL real (Testcontainers).
- Devolver 200 con `{eventoId, sectores, asientos, idempotente}`.

### 3.2 Reserva de asientos numerados (US-12)

Endpoint: `POST /api/inventory/reservations` (parte numerada).

- `SELECT ... FOR UPDATE` de los asientos ordenados por `id` para evitar deadlocks.
- Si alguno no está `LIBRE` → rollback y 409 `SEAT_NOT_AVAILABLE`; no se reserva ninguno.
- Si todos están libres → `RESERVADO`, `reserva_id` y `expires_at = now() + 10 min`; 201.
- Tests: asiento libre, asiento ocupado, reserva múltiple parcialmente no disponible.
- Reintentar la transacción ante deadlock (máximo 3 intentos).

### 3.3 Reserva de zonas no numeradas (US-13)

- `UPDATE sector_inventario SET disponibles = disponibles - N WHERE evento_id = ? AND sector_id = ? AND disponibles >= N RETURNING disponibles`.
- Si no devuelve fila → 409 `INSUFFICIENT_AVAILABILITY`, sin cambios.
- Si hay fila → 201 y disponibilidad descontada.
- Tests: suficiente, insuficiente, dos reservas concurrentes por el último lugar.

### 3.4 Límite por cliente (US-14)

- `pg_advisory_xact_lock(hashtext(evento_id || cliente_id))` antes de contar.
- Contar entradas vendidas + reservas `VIGENTE` del cliente para ese evento.
- Si la suma + lo pedido supera `limite_por_cliente` → 422 `CLIENT_LIMIT_EXCEEDED` sin reservar.
- Tests: 3 entradas y límite 4, intenta 2 más → 422; con 1 entrada, 2 más → 201; concurrencia del mismo cliente.

### 3.5 Vencimiento y liberación (US-15)

- Scheduler cada 30 s: reservas `VIGENTE` con `expires_at < now()` → `VENCIDA` y liberar ítems.
- Los updates son condicionales (`WHERE estado = 'RESERVADO' AND reserva_id = ?`) para ser idempotentes con varias instancias.
- Validación perezosa: al consultar/confirmar una reserva vencida se libera y se responde 410 `RESERVATION_EXPIRED` al pagar.
- Tests: liberación de asientos y de zona, pago sobre reserva vencida → 410.

### 3.6 Confirmación, liberación y devolución (US-17, US-18, US-23)

Endpoints internos:

- `POST /internal/reservations/{id}/confirm`: `UPDATE reserva SET estado='CONFIRMADA' WHERE id=? AND estado='VIGENTE' AND expires_at > now()`; asientos `RESERVADO → VENDIDO`; idempotente (repetir sobre `CONFIRMADA` devuelve 200).
- `POST /internal/reservations/{id}/release`: libera asientos/zona y marca `LIBERADA`; idempotente (204).
- `POST /internal/returns`: libera asientos vendidos o repone disponibilidad por devolución; idempotente (204).
- Tests: confirmar/liberar dos veces no cambia el resultado; devolución de asiento y de zona.

### 3.7 Prueba de concurrencia (US-20) — **crítica**

Con PostgreSQL real vía Testcontainers (no H2):

- 100 hilos intentan reservar el mismo asiento: exactamente **1** éxito y **99** respuestas 409.
- Varios hilos intentan el último lugar de una zona: exactamente **1** éxito.
- Al terminar, la base tiene una sola reserva/venta para ese asiento y la disponibilidad de la zona es 0.
- El test debe correr en CI (Docker disponible en GitHub Actions).

### 3.8 OpenAPI y README

- OpenAPI con ejemplos y códigos 201/400/404/409/410/422.
- `openapi/inventory-openapi.json` exportado.
- README del servicio con responsabilidad, endpoints, modelo, variables y comandos.

## 4. Modelo de datos (ya migrado en `V1__init.sql`)

Tablas: `evento_inventario`, `sector_inventario`, `asiento_inventario`, `reserva`, `reserva_item`, `cliente_evento`.

- `asiento_inventario.estado` tiene check `LIBRE/RESERVADO/VENDIDO` y coherencia con `reserva_id`/`expires_at`.
- `sector_inventario.disponibles` es `NULL` en numerados y un entero en no numerados.
- `reserva.estado` tiene check `VIGENTE/CONFIRMADA/LIBERADA/VENCIDA`.
- `cliente_evento` es la tabla de conteo por cliente.

Si necesitás un índice nuevo, `V2__*.sql`; no edites `V1`.

## 5. Ramas sugeridas

| Rama | Tarea |
|---|---|
| `feat/inventory-modelo-alta` | 3.1 |
| `feat/inventory-reserva-asientos` | 3.2 |
| `feat/inventory-reserva-zonas` | 3.3 |
| `feat/inventory-limite-cliente` | 3.4 |
| `feat/inventory-vencimiento` | 3.5 |
| `feat/inventory-confirmacion-liberacion` | 3.6 |
| `test/inventory-concurrencia` | 3.7 |
| `docs/inventory-openapi` | 3.8 |

## 6. PR

Título: `feat(inventory): <tema> (tarea 3.x)`

En el cuerpo:

- Estrategia de atomicidad usada (SQL exacto o método).
- Salida de `mvn test`, incluida la prueba de concurrencia.
- Requests/responses de ejemplo.
- Confirmación de contrato y OpenAPI actualizados.

## 7. Coordinación

- **Con P1 (catalog):** formato del snapshot de publicación y callback de disponibilidad (`PATCH /internal/events/{id}/availability`).
- **Con P3 (orders):** contrato de confirmar/liberar/devolver y semántica de 409/410.
- **Con P4 (gateway):** las rutas públicas de disponibilidad y asientos no deben pedir identidad.
- **Con P5 (calidad):** entregar el test de concurrencia como evidencia para `docs/pruebas.md`.

## 8. Terminado cuando

- [ ] 3.1 a 3.8 marcadas en `tasks.md`.
- [ ] `mvn test` verde, incluida la prueba de concurrencia.
- [ ] La prueba de concurrencia corre en CI.
- [ ] Swagger UI y `openapi/inventory-openapi.json` completos.
- [ ] README del servicio actualizado.
- [ ] CI en verde y PR aprobado por otro integrante.
