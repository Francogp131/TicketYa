# orders-service

Servicio de órdenes, pagos, entradas/QR y compensaciones de TicketYa.

- **Puerto:** 8083
- **Base:** `orders_db` (PostgreSQL 16, puerto 5435)
- **Responsable:** P3 — ver [`docs/equipo/compa-3.md`](../docs/equipo/compa-3.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 5.

## Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/orders` | autenticado | Crea la orden, cobra y emite entradas; requiere `Idempotency-Key` |
| GET | `/api/orders` | autenticado | Lista paginada de las órdenes del cliente |
| GET | `/api/orders/{ordenId}` | dueño | Detalle de la orden con sus entradas |
| GET | `/api/orders/tickets` | autenticado | Lista paginada de las entradas propias |
| GET | `/api/orders/tickets/{entradaId}` | dueño | Detalle de una entrada propia |
| POST | `/api/orders/tickets/{entradaId}/returns` | dueño | Solicita la devolución de una entrada |
| POST | `/api/orders/returns/{devolucionId}/approve` | ADMIN | Libera el lugar, invalida la entrada y reintegra |
| POST | `/api/orders/returns/{devolucionId}/reject` | ADMIN | Rechaza la devolución; la entrada sigue válida |
| POST | `/internal/events/{eventoId}/cancel` | interno | Marca las entradas vendidas `EN_REINTEGRO` y solicita los reintegros |

## Flujo de compra

1. Valida `Idempotency-Key` (8–100 caracteres) y el `token` de la pasarela.
2. Reclama la clave en `idempotency_key` con estado `EN_PROCESO` en una transacción propia.
   Si ya existe: misma respuesta (misma clave y cuerpo), `422 IDEMPOTENCY_KEY_REUSED`
   (cuerpo distinto) o `409 IDEMPOTENCY_IN_PROGRESS` con `Retry-After`.
3. Consulta la reserva a inventory (`GET /internal/reservations/{id}`), valida dueño y vigencia.
4. Guarda el snapshot del evento, los ítems y el pago `PENDIENTE`; el total se calcula en el servidor.
5. Llama a la pasarela mock (`PaymentGateway`): `APROBADO`, `RECHAZADO` o `LENTO` (timeout).
   - `APROBADO`: confirma la reserva en inventory, pasa la orden a `PAGADA` y emite una
     entrada con QR único por asiento y por unidad de zona.
   - `RECHAZADO`: orden `RECHAZADA`, sin entradas, y libera la reserva.
   - `LENTO`: `502 PAYMENT_GATEWAY_ERROR`, libera la reserva y no deja orden pagada.
6. Si falla la confirmación de inventory o la emisión de entradas después del pago, la orden
   queda `REINTEGRO_PENDIENTE`, el pago `REINTEGRADO` y se liberan o devuelven los lugares.
7. Al completar, guarda la respuesta en `idempotency_key` para que los reintentos devuelvan
   exactamente la misma orden y las mismas entradas.

## Modelo

`orden`, `orden_item`, `pago`, `entrada`, `devolucion` e `idempotency_key`. Flyway en
[`src/main/resources/db/migration`](src/main/resources/db/migration).

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8083` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5435/orders_db` | Conexión JDBC |
| `DB_USER` / `DB_PASSWORD` | `ticketya` | Credenciales |
| `INVENTORY_URL` | `http://localhost:8082` | Cliente interno hacia inventory |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d orders-db
cd orders-service
mvn test
mvn spring-boot:run
curl http://localhost:8083/actuator/health
```

`mvn test` incluye `OrderIdempotencyIntegrationTest`, que usa PostgreSQL 16 con
Testcontainers para verificar que dos reintentos concurrentes con la misma clave crean
una sola orden. Si Docker no está disponible, el test se omite.

Swagger UI: <http://localhost:8083/swagger-ui.html> · OpenAPI: <http://localhost:8083/v3/api-docs>
