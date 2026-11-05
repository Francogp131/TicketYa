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

La prueba de concurrencia corre con `mvn test` contra PostgreSQL real (Testcontainers): 100 hilos sobre el mismo asiento y varios sobre el último lugar de una zona. Vencimiento, límite por cliente, confirmación y OpenAPI corresponden a las tareas 3.3–3.6 y 3.8.

## Reserva y no doble venta

- La reserva se ejecuta en una transacción.
- Asientos numerados: se bloquean con `SELECT ... FOR UPDATE` ordenados por `asientoId`; si alguno no está `LIBRE`, se aborta toda la reserva con `409 SEAT_NOT_AVAILABLE`.
- Zonas no numeradas: `UPDATE ... SET disponibles = disponibles - N WHERE disponibles >= N RETURNING disponibles`; si no hay filas, `409 INSUFFICIENT_AVAILABILITY`.
- La reserva vence a los 10 minutos (`expires_at`).
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

Swagger UI: <http://localhost:8082/swagger-ui.html> · OpenAPI: <http://localhost:8082/v3/api-docs>
