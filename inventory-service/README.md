# inventory-service

Servicio de disponibilidad, reservas temporales y no doble venta de TicketYa.

- **Puerto:** 8082
- **Base:** `inventory_db` (PostgreSQL 16, puerto 5434)
- **Responsable:** P2 — ver [`docs/equipo/compa-2.md`](../docs/equipo/compa-2.md)
- **Contrato:** [`docs/api-contract.md`](../docs/api-contract.md), sección 4.

## Endpoints

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/internal/events/{eventoId}/inventory` | interno | Alta/actualización idempotente del snapshot de catalog |
| GET | `/api/inventory/events/{eventoId}/availability` | público | Disponibilidad por sector |
| GET | `/api/inventory/events/{eventoId}/seats?sectorId=` | público | Mapa de asientos |
| POST | `/api/inventory/reservations` | autenticado | Reserva atómica de asientos numerados y zonas |
| GET | `/api/inventory/reservations/{reservaId}` | dueño | Consulta de una reserva (valida vencimiento) |
| DELETE | `/api/inventory/reservations/{reservaId}` | dueño | Libera la reserva; idempotente, 204 |
| GET | `/internal/reservations/{reservaId}` | interno | Reserva para orders, con nombre/fecha del evento |
| POST | `/internal/reservations/{reservaId}/confirm` | interno | Confirma la venta y marca los asientos `VENDIDO` |
| POST | `/internal/reservations/{reservaId}/release` | interno | Libera una reserva vigente (pago rechazado/error) |
| POST | `/internal/returns` | interno | Libera lugares por devolución aprobada; acepta `referencia` para deduplicar |

## Reserva y no doble venta

- La reserva se ejecuta en una transacción.
- Asientos numerados: se bloquean con `SELECT ... FOR UPDATE` ordenados por `asientoId`; si alguno no está `LIBRE`, se aborta toda la reserva con `409 SEAT_NOT_AVAILABLE`.
- Zonas no numeradas: `UPDATE ... SET disponibles = disponibles - N WHERE disponibles >= N RETURNING disponibles`; si no hay filas, `409 INSUFFICIENT_AVAILABILITY`.
- Límite por cliente: suma entradas vendidas (`cliente_evento`) y cantidades de reservas `VIGENTE` no vencidas; si se supera, `422 CLIENT_LIMIT_EXCEEDED`.
- La reserva vence a los 10 minutos (`expires_at`). Un job cada 30 s libera las vencidas y la consulta/pago hacen validación perezosa.
- Confirmar una reserva la pasa a `CONFIRMADA`, los asientos a `VENDIDO` y suma `entradas_vendidas`; repetirlo es idempotente.
- Liberar dos veces es idempotente. Una devolución con `referencia` repetida no repone dos veces la zona.
- El alta de inventario es idempotente por `eventoId + version` y usa `ON CONFLICT DO NOTHING` para los asientos.

## Modelo

`evento_inventario`, `sector_inventario`, `asiento_inventario`, `reserva`, `reserva_item`,
`cliente_evento` y `devolucion_procesada`. Flyway en
[`src/main/resources/db/migration`](src/main/resources/db/migration).

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8082` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5434/inventory_db` | Conexión JDBC |
| `DB_USER` / `DB_PASSWORD` | `ticketya` | Credenciales |
| `INVENTORY_VENCIMIENTO_MS` | `30000` | Frecuencia del job de reservas vencidas |

## Ejecutar y probar

```bash
docker compose -f docker-compose.dev.yml up -d inventory-db
cd inventory-service
mvn test
mvn spring-boot:run
curl http://localhost:8082/actuator/health
```

`mvn test` incluye `ReservaConcurrencyIntegrationTest`, que usa PostgreSQL 16 con
Testcontainers para lanzar 100 hilos sobre el mismo asiento y 40 sobre el último lugar
de una zona. Si Docker no está disponible, el test se omite.

Swagger UI: <http://localhost:8082/swagger-ui.html> · OpenAPI: <http://localhost:8082/v3/api-docs>
