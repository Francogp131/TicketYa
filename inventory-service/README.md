# inventory-service

Servicio de disponibilidad, reservas temporales y no doble venta de TicketYa.

- **Puerto:** 8082
- **Base:** `inventory_db` (PostgreSQL 16, puerto 5434)
- **Responsable:** P2 — ver [`docs/equipo/compa-2.md`](../docs/equipo/compa-2.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 4.

## Endpoints implementados (Día 1)

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/internal/events/{eventoId}/inventory` | interno | Alta/actualización idempotente del snapshot de catalog |
| GET | `/api/inventory/events/{eventoId}/availability` | público | Disponibilidad por sector |
| GET | `/api/inventory/events/{eventoId}/seats?sectorId=` | público | Mapa de asientos |
| POST | `/api/inventory/reservations` | autenticado | Reserva atómica de asientos numerados y zonas |
| GET | `/api/inventory/reservations/{reservaId}` | dueño | Consulta de una reserva |
| GET | `/internal/reservations/{reservaId}` | interno | Reserva para orders, con nombre/fecha del evento |
| POST | `/internal/reservations/{reservaId}/confirm` | interno | Confirma la venta. Repetir una confirmada devuelve 200 |
| POST | `/internal/reservations/{reservaId}/release` | interno | Libera la reserva si el pago se rechazó. Idempotente, 204 |
| POST | `/internal/returns` | interno | Devuelve un asiento vendido o repone una zona. Idempotente con `Idempotency-Key`, 204 |

Límite por cliente, vencimiento, confirmación/liberación y devolución corresponden a las tareas 3.4–3.8. La prueba de concurrencia corre con `mvn test` contra PostgreSQL real (Testcontainers): 100 hilos sobre el mismo asiento y varios sobre el último lugar de una zona. El OpenAPI exportado está en [`../openapi/inventory-openapi.json`](../openapi/inventory-openapi.json) e incluye ejemplos y los códigos 201, 400, 404, 409, 410 y 422.

## Reserva y no doble venta

- La reserva se ejecuta en una transacción.
- Asientos numerados: se bloquean con `SELECT ... FOR UPDATE` ordenados por `asientoId`; si alguno no está `LIBRE`, se aborta toda la reserva con `409 SEAT_NOT_AVAILABLE`.
- Zonas no numeradas: `UPDATE ... SET disponibles = disponibles - N WHERE disponibles >= N RETURNING disponibles`; si no hay filas, `409 INSUFFICIENT_AVAILABILITY`.
- Límite por cliente: `pg_advisory_xact_lock(hashtext(evento_id || cliente_id))` y después se suman `cliente_evento.entradas_vendidas` más las cantidades de reservas `VIGENTE`. Si eso más lo pedido supera `limite_por_cliente`, se responde `422 CLIENT_LIMIT_EXCEEDED` y no se escribe la reserva. Al confirmar una venta (tarea 3.6) hay que incrementar `entradas_vendidas`, porque la reserva pasa a `CONFIRMADA` y deja de entrar en el conteo de vigentes.
- La reserva vence a los 10 minutos (`expires_at`). Un scheduler cada 30 segundos pasa a `VENCIDA` las reservas `VIGENTE` con `expires_at` cumplido y libera sus lugares. Los asientos se sueltan con `UPDATE ... WHERE estado = 'RESERVADO' AND reserva_id = ?`, así dos instancias no liberan dos veces. Consultar una reserva vencida también la libera y responde `estado: VENCIDA`. Pagar una vencida responde `410 RESERVATION_EXPIRED` (lo valida la confirmación de la tarea 3.6).
- Confirmar: `UPDATE reserva SET estado = 'CONFIRMADA' WHERE id = ? AND estado = 'VIGENTE' AND expires_at > now()`. Los asientos pasan de `RESERVADO` a `VENDIDO` y se suma `cliente_evento.entradas_vendidas`. Si ya estaba `CONFIRMADA`, se devuelve la misma reserva. Si venció, se liberan los lugares y se responde `410 RESERVATION_EXPIRED`.
- Liberar: la reserva `VIGENTE` pasa a `LIBERADA` y se reponen asientos y zona. Repetir la llamada responde 204 sin tocar la disponibilidad otra vez.
- Devolver: un asiento `VENDIDO` vuelve a `LIBRE`; una zona suma la cantidad a `disponibles`. Con el header `Idempotency-Key`, repetir el mismo cuerpo no repone dos veces.
- Concurrencia: `ConcurrenciaPostgresTest` verifica que un asiento y el último lugar de una zona se reservan una sola vez.
- El alta de inventario es idempotente por `eventoId + version` y usa `ON CONFLICT DO NOTHING` para los asientos.

## Modelo

`evento_inventario`, `sector_inventario`, `asiento_inventario`, `reserva`, `reserva_item` y `cliente_evento`. Flyway en [`src/main/resources/db/migration`](src/main/resources/db/migration).

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8082` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5434/inventory_db` | Conexión JDBC |
| `DB_USER` / `DB_PASSWORD` | `ticketya` | Credenciales |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d inventory-db
cd inventory-service
mvn test
mvn spring-boot:run
curl http://localhost:8082/actuator/health
```

Swagger UI: <http://localhost:8082/swagger-ui.html> · OpenAPI en vivo: <http://localhost:8082/v3/api-docs>

El archivo versionado se regenera con `mvn test -Dexport.openapi=true` (lo escribe `OpenApiDocumentoTest`).
