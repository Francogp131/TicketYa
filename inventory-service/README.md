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

Vencimiento, límite por cliente, la prueba de concurrencia y el OpenAPI corresponden a las tareas 3.4, 3.5, 3.7 y 3.8.

## Reserva y no doble venta

- La reserva se ejecuta en una transacción.
- Asientos numerados: se bloquean con `SELECT ... FOR UPDATE` ordenados por `asientoId`; si alguno no está `LIBRE`, se aborta toda la reserva con `409 SEAT_NOT_AVAILABLE`.
- Zonas no numeradas: `UPDATE ... SET disponibles = disponibles - N WHERE disponibles >= N RETURNING disponibles`; si no hay filas, `409 INSUFFICIENT_AVAILABILITY`.
- La reserva vence a los 10 minutos (`expires_at`).
- Confirmar: `UPDATE reserva SET estado = 'CONFIRMADA' WHERE id = ? AND estado = 'VIGENTE' AND expires_at > now()`. Los asientos pasan de `RESERVADO` a `VENDIDO` y se suma `cliente_evento.entradas_vendidas`. Si ya estaba `CONFIRMADA`, se devuelve la misma reserva. Si venció, se liberan los lugares y se responde `410 RESERVATION_EXPIRED`.
- Liberar: la reserva `VIGENTE` pasa a `LIBERADA` y se reponen asientos y zona. Repetir la llamada responde 204 sin tocar la disponibilidad otra vez.
- Devolver: un asiento `VENDIDO` vuelve a `LIBRE`; una zona suma la cantidad a `disponibles`. Con el header `Idempotency-Key`, repetir el mismo cuerpo no repone dos veces.
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

Swagger UI: <http://localhost:8082/swagger-ui.html> · OpenAPI: <http://localhost:8082/v3/api-docs>
