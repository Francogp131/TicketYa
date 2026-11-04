# P3 — Orders (Caro)

**Servicio:** `orders-service` (puerto **8083**, base `orders_db` en **5435**)
**Rama base:** `main`
**Tareas OpenSpec:** 4.1 a 4.11
**Estado al 4/11:** 4.1 y 4.2 cerradas en el PR #1. Arrancás con **4.3 (pasarela mock)** y **4.4 (pago aprobado + QR)**.

## 1. Antes de empezar

1. Leer [`../api-contract.md`](../api-contract.md) (sección 5 y catálogo de errores).
2. Leer `openspec/changes/entrega-1-core/specs/reserva-y-compra/spec.md` y `specs/devoluciones-cancelacion/spec.md`.
3. Leer `design.md`, decisiones 4, 7, 8, 9 y 10 (saga de compra, idempotencia, pasarela mock y devoluciones).
4. Levantar la base:

```bash
docker compose -f docker-compose.dev.yml up -d orders-db
cd orders-service
mvn test
mvn spring-boot:run
curl http://localhost:8083/actuator/health
```

Swagger: <http://localhost:8083/swagger-ui.html>

## 2. Alcance

Sos dueño de todo lo que está dentro de `orders-service/`:

- Modelo JPA: `Orden`, `OrdenItem`, `Pago`, `Entrada`, `Devolucion`, `IdempotencyKey`.
- Servicio de compra, orquestación de la saga, compensaciones.
- Pasarela de pagos mock detrás de una interfaz.
- Devoluciones, cancelación de evento y consulta de entradas.
- Cliente REST hacia inventory.
- OpenAPI y README del servicio.

**No toques** catalog, inventory ni gateway.

## 3. Tareas y criterios de aceptación

### 4.1 Modelo y migración (US-16)

- Entidades y repositorios para `orden`, `orden_item`, `pago`, `entrada`, `devolucion`, `idempotency_key`.
- La migración `V1__init.sql` ya crea las tablas; verificá que JPA mapee exactamente (nombres, enums, `NUMERIC`, `TIMESTAMPTZ`).
- Tests: contexto levanta contra PostgreSQL (Testcontainers) y las constraints funcionan.

### 4.2 Crear orden desde reserva (US-16)

Endpoint: `POST /api/orders` (primera parte, sin pago).

- Consulta la reserva a inventory (`GET /internal/reservations/{id}`).
- Valida dueño (`clienteId == X-User-Id`) → 403 `NOT_RESERVATION_OWNER`; inexistente → 404; vencida → 410 `RESERVATION_EXPIRED`.
- Guarda snapshot de evento, sector, asiento y precio; **el total se calcula en el servidor**, nunca se acepta del cliente.
- Crea la orden `PENDIENTE_PAGO` → 201.
- Tests: 201, 403, 404, 410 y total calculado.

### 4.3 Pasarela mock (US-17, US-18)

Interfaz `PaymentGateway` + implementación mock:

- `cobrar(ordenId, monto, medioPago, token)`.
- Token `APROBADO` → aprobado; `RECHAZADO` → rechazado; `LENTO` → demora configurable (para probar timeout); otro → rechazado.
- Timeout de 5 s; si expira → `PAYMENT_GATEWAY_ERROR` (502) y no se marca la orden `PAGADA`.
- `reintegrar(pagoId)` idempotente.
- Tests de cada resultado y del timeout con un mock que demora.

### 4.4 Pago aprobado y emisión de entradas (US-17)

- Pago aprobado → confirmar reserva en inventory (`POST /internal/reservations/{id}/confirm`).
- Marcar orden `PAGADA`, guardar medio de pago y una `entrada` por lugar con `qr_code` UUID único.
- Si la reserva es de zona no numerada, emitir N entradas (una por lugar) sin asiento.
- Tests: una entrada por asiento, QR únicos, orden `PAGADA`.

### 4.5 Pago rechazado y reserva vencida (US-15, US-18)

- Pago rechazado → liberar reserva en inventory (`POST /internal/reservations/{id}/release`, motivo `PAGO_RECHAZADO`), orden `RECHAZADA`, sin entradas.
- Reserva vencida → 410, sin orden pagada y sin entradas.
- Tests: estado de la orden y disponibilidad liberada.

### 4.6 Idempotencia (US-21) — **crítica**

- `Idempotency-Key` obligatoria en `POST /api/orders`.
- Insertar con `ON CONFLICT DO NOTHING` en `idempotency_key`.
- Misma clave + mismo cuerpo → devolver la respuesta guardada, sin nueva orden ni cobro.
- Misma clave + cuerpo distinto → 422 `IDEMPOTENCY_KEY_REUSED`.
- Clave `EN_PROCESO` → 409 `IDEMPOTENCY_IN_PROGRESS` con `Retry-After`.
- Tests: reintento secuencial y dos concurrentes con la misma clave crean **una sola** orden.

### 4.7 Compensaciones (US-18)

- Si inventory no confirma después del pago: solicitar reintegro, liberar reserva, orden `ERROR_PAGO` o `REINTEGRO_PENDIENTE`; **nunca** `PAGADA`.
- Si falla la emisión de entradas después de confirmar: liberar lugares, reintegro y ninguna entrada válida.
- Tests con inventory simulado que falla después del pago.

### 4.8 Devoluciones (US-22, US-23)

- `POST /api/orders/tickets/{entradaId}/returns`: dueño, entrada `VALIDA`, sin devolución abierta, `eventoFecha - now() > 72 h` → `PENDIENTE` (201).
- `POST /api/orders/returns/{id}/approve` (ADMIN): inventory libera, entrada `DEVUELTA`, refund mock, `APROBADA`; si inventory falla → `PROCESANDO` y 502.
- `POST /api/orders/returns/{id}/reject` (ADMIN): `RECHAZADA`, la entrada sigue `VALIDA`.
- Tests: 422 fuera de plazo, 409 ya resuelta, 403 entrada ajena, liberación + invalidación + refund al aprobar, entrada válida al rechazar.

### 4.9 Cancelación de evento (US-24)

Endpoint interno: `POST /internal/events/{eventoId}/cancel` (lo llama catalog).

- Pasa todas las entradas vendidas a `EN_REINTEGRO` y solicita el reintegro.
- Idempotente: repetir no duplica reintegros.
- Tests: evento con entradas y sin entradas.

### 4.10 Consulta de entradas y órdenes (US-19)

- `GET /api/orders` y `GET /api/orders/{id}`: solo las del cliente autenticado.
- `GET /api/orders/tickets` y `GET /api/orders/tickets/{id}`: solo las propias, con evento, sector, asiento y QR.
- Entrada/orden ajena → 403; inexistente → 404.
- Tests de aislamiento entre dos clientes.

### 4.11 OpenAPI y README

- OpenAPI con ejemplos y códigos 201/400/403/404/409/410/422/502.
- `openapi/orders-openapi.json` exportado.
- README del servicio con responsabilidad, endpoints, modelo, variables y comandos.

## 4. Modelo de datos (ya migrado en `V1__init.sql`)

Tablas: `orden`, `orden_item`, `pago`, `entrada`, `devolucion`, `idempotency_key`.

- `orden.estado` tiene check con los cinco estados.
- `entrada.qr_code` es UUID único.
- `devolucion` tiene un índice único parcial: una sola devolución `PENDIENTE`/`PROCESANDO` por entrada.
- `idempotency_key.response_body` es `JSONB`.

Si necesitás un índice nuevo, `V2__*.sql`; no edites `V1`.

## 5. Ramas sugeridas

| Rama | Tarea |
|---|---|
| `feat/orders-modelo` | 4.1 |
| `feat/orders-crear-orden` | 4.2 |
| `feat/orders-pasarela-mock` | 4.3 |
| `feat/orders-pago-aprobado` | 4.4 |
| `feat/orders-pago-rechazado` | 4.5 |
| `feat/orders-idempotencia` | 4.6 |
| `feat/orders-compensaciones` | 4.7 |
| `feat/orders-devoluciones` | 4.8 |
| `feat/orders-cancelacion` | 4.9 |
| `feat/orders-consultas` | 4.10 |
| `docs/orders-openapi` | 4.11 |

## 6. PR

Título: `feat(orders): <tema> (tarea 4.x)`

En el cuerpo:

- Flujo implementado y estados que toca.
- Salida de `mvn test`.
- Evidencia del caso idempotente y del compensado (logs o tests).
- Requests/responses de ejemplo.
- Confirmación de contrato y OpenAPI actualizados.

## 7. Coordinación

- **Con P2 (inventory):** contrato de `GET /internal/reservations/{id}`, `confirm`, `release` y `returns`; semántica de 409/410.
- **Con P1 (catalog):** cancelación de evento (`POST /internal/events/{id}/cancel`) y snapshot de nombre/fecha.
- **Con P4 (gateway):** rutas de devoluciones aprobar/rechazar deben exigir ADMIN.
- **Con P5 (calidad):** avisar cuando el flujo de compra esté listo para la prueba E2E.

## 8. Terminado cuando

- [ ] 4.1 a 4.11 marcadas en `tasks.md`.
- [ ] `mvn test` verde en `orders-service`.
- [ ] Test de idempotencia concurrente pasando.
- [ ] Swagger UI y `openapi/orders-openapi.json` completos.
- [ ] README del servicio actualizado.
- [ ] CI en verde y PR aprobado por otro integrante.
